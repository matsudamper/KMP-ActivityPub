package net.matsudamper.kmp.activitypub

import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.matsudamper.kmp.activitypub.admin.AdminSessionInMemoryStore
import net.matsudamper.kmp.activitypub.delivery.DeliveryCircuitBreaker
import net.matsudamper.kmp.activitypub.delivery.DeliveryRetryPolicy
import net.matsudamper.kmp.activitypub.delivery.DeliveryWorker
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.DomainBlockService
import net.matsudamper.kmp.activitypub.logic.NoteEnqueuer
import net.matsudamper.kmp.activitypub.logic.RepositoryActorProfiles
import net.matsudamper.kmp.activitypub.logic.RepositoryEarlyUndoneLikes
import net.matsudamper.kmp.activitypub.logic.RepositoryFavouriteStore
import net.matsudamper.kmp.activitypub.logic.RepositoryFollowerStore
import net.matsudamper.kmp.activitypub.logic.RepositoryNoteStore
import net.matsudamper.kmp.activitypub.logic.RepositoryStampStore
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.session.LoginFailureStore
import net.matsudamper.kmp.activitypub.session.LoginThrottle
import net.matsudamper.kmp.activitypub.session.UserSessionService
import net.matsudamper.kmp.activitypub.session.UserSessionStore
import net.matsudamper.kmp.activitypub.session.ValkeyLoginFailureStore
import net.matsudamper.kmp.activitypub.session.ValkeyUserSessionStore
import net.matsudamper.kmp.activitypub.staticfiles.StaticFiles
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kotpub.actor.ActorAppearance
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorKey
import net.matsudamper.kotpub.actor.ActorKeyLoader
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.HttpRemoteActors
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.actor.StoredActorAppearances
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.actor.StoredActorProfiles
import net.matsudamper.kotpub.delivery.ActivityDelivery
import net.matsudamper.kotpub.delivery.HttpActivityDelivery
import net.matsudamper.kotpub.favourite.FavouriteStore
import net.matsudamper.kotpub.follower.FollowerStore
import net.matsudamper.kotpub.http.KtorActivityPubHttpClient
import net.matsudamper.kotpub.inbox.InboxDomainBlocks
import net.matsudamper.kotpub.inbox.InboxService
import net.matsudamper.kotpub.note.FollowBackfillPublisher
import net.matsudamper.kotpub.note.NotePublisher
import net.matsudamper.kotpub.note.NoteStore
import net.matsudamper.kotpub.url.WebPageUrls

/**
 * 外から作れるようにしてあるのはテストのため。フェイクを渡せば、
 * 本物の DB や外向きの HTTP を用意せずにルーティングを組み立てられる。
 * 本番の組み立ては [create] にある。
 *
 * @param remoteActors 相手のアクターの引き先。署名検証に使う公開鍵と、
 *   `Accept` の宛先になる inbox をここから取る。本番は [HttpRemoteActors] が
 *   相手のサーバーに GET しに行く
 */
class AppDependencies(
    val repositories: Repositories,
    val actorKey: ActorKey,
    private val sessionStore: UserSessionStore,
    private val loginFailureStore: LoginFailureStore,
    val env: ServerEnv,
    val remoteActors: RemoteActors,
    val delivery: ActivityDelivery,
    val adminSessionStore: AdminSessionInMemoryStore = AdminSessionInMemoryStore(),
) : AutoCloseable {
    val staticFiles: StaticFiles? = env.staticSrcDir?.let { StaticFiles(it) }?.takeIf { it.isAvailable() }

    /**
     * 相手に渡す画面の URL。アカウントと投稿の画面はまだ無いので出さない。
     *
     * 出さなければ相手は `id` に倒すので、開けないページを指すより JSON のパスが開く方がまだ読める
     */
    val webPageUrls: WebPageUrls? = null

    val followerStore: FollowerStore = RepositoryFollowerStore(repositories.followers)

    val noteStore: NoteStore = RepositoryNoteStore(repositories.notes)

    val favouriteStore: FavouriteStore = RepositoryFavouriteStore(repositories.noteFavourites)

    // 毎回引き直す。持ち回すと、追加したアカウントが引けるようになるまで間が空く
    val directory: ActorDirectory = ActorDirectory(
        domain = env.domain,
        stored = object : StoredActorNames {
            override fun find(username: String): String? {
                return repositories.accountRepository.findByUsername(username)?.username
            }

            override fun finds(usernames: Set<String>): Map<String, String> {
                return repositories.accountRepository.findByUsernames(usernames).mapValues { it.value.username }
            }
        },
    )

    /**
     * 消したアカウントだけを引ける名前の引き先。
     *
     * 消した後も `Delete{Actor}` を送り切るまでは、そのアカウントとして署名できる
     * 必要がある。外から見える引き当て（[directory]）に混ぜると、消したアカウントが
     * WebFinger や Actor から見えたままになる。
     *
     * 生きているアカウントは返さない。どちらも引ける 1 つの口にすると、消した後に
     * 投函された行まで旧アクターとして送れてしまう
     */
    private val deletedActorDirectory: ActorDirectory = ActorDirectory(
        domain = env.domain,
        stored = object : StoredActorNames {
            override fun find(username: String): String? = repositories.accountRepository.findDeletedByUsername(username)?.username

            override fun finds(usernames: Set<String>): Map<String, String> {
                return usernames.mapNotNull { username -> find(username)?.let { username to it } }.toMap()
            }
        },
    )

    /**
     * プロフィールのリンクと画像を設定する口がまだ無いので、何も出さない
     */
    val actorAppearances: StoredActorAppearances = object : StoredActorAppearances {
        override fun find(username: String): ActorAppearance = ActorAppearance.EMPTY
    }

    val actorProfiles: StoredActorProfiles = RepositoryActorProfiles(repositories.accountRepository)

    val domainBlockService: DomainBlockService = DomainBlockService(
        domainBlocks = repositories.domainBlocks,
        clock = Instant::now,
    )

    val inboxService: InboxService = InboxService.default(
        directory = directory,
        remoteActors = remoteActors,
        followers = followerStore,
        favourites = favouriteStore,
        stamps = RepositoryStampStore(repositories.noteStamps),
        earlyUndoneLikes = RepositoryEarlyUndoneLikes(repositories.earlyUndoneLikes),
        domainBlocks = object : InboxDomainBlocks {
            override fun blocksInboxFrom(url: String): Boolean = domainBlockService.blocksInboxFrom(url)

            override fun signedRequestReceived(verifiedSignerActorId: String) {
                domainBlockService.markAvailable(verifiedSignerActorId)
            }
        },
        domain = env.domain,
    )

    /**
     * フォローが成立した相手に、フォローより前の投稿を配る。
     *
     * 成立するのは `Accept` を送れたときなので、始めるのは配信ワーカーになる。
     * 配り終える前にプロセスが落ちたら、その分は届かない。フォロー自体は
     * 成立しているので、次の新着からは普通に届く
     */
    private val followBackfillPublisher: FollowBackfillPublisher = FollowBackfillPublisher(
        notes = noteStore,
        delivery = delivery,
        webPages = webPageUrls,
    )

    val notePublisher: NotePublisher = NotePublisher(
        notes = noteStore,
        webPages = webPageUrls,
    )

    val noteEnqueuer: NoteEnqueuer = NoteEnqueuer(
        publisher = notePublisher,
        followers = repositories.followers,
        deliveryQueue = repositories.deliveryQueue,
        domainBlocks = domainBlockService,
    )

    val accountService: AccountService = AccountService(
        accountRepository = repositories.accountRepository,
        domain = env.domain,
    )

    val sessionService: UserSessionService = UserSessionService(
        sessionStore = sessionStore,
        ttl = 30.days,
        clock = Clock.systemUTC(),
    )

    val loginThrottle: LoginThrottle = LoginThrottle(
        failureStore = loginFailureStore,
        maxFailures = 5,
        lockDuration = 30.days,
    )

    private val deliveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val backgroundStopped = AtomicBoolean(false)

    /**
     * @return 片付いた削除済みアカウントの数
     */
    fun startDeliveryWorker(): Int {
        // 送る配信が無くなった削除済みアカウントをここで片付ける。名前が空くのもここ。
        // 動いている間に空になった分は次の起動まで残る
        val purgedAccounts = repositories.accountRepository.purgeDeleted()

        DeliveryWorker(
            queue = repositories.deliveryQueue,
            delivery = delivery,
            directory = directory,
            deletedActorDirectory = deletedActorDirectory,
            retryPolicy = DeliveryRetryPolicy(
                initialInterval = 30.seconds,
                maxInterval = 24.hours,
                giveUpAfter = 7.days,
            ),
            backfill = followBackfillPublisher,
            circuitBreaker = DeliveryCircuitBreaker(failureThreshold = 10, coolOff = 60.seconds),
            domainBlocks = domainBlockService,
            claimLimit = 100,
            sendConcurrency = 8,
            idleInterval = 1.seconds,
            clock = Instant::now,
            openTelemetry = OpenTelemetryInitializer.get(),
        ).start(deliveryScope)

        return purgedAccounts
    }

    /**
     * 配信のワーカーとフォロー成立後の再配信を止めて、走っている分が終わるまで待つ。
     *
     * 待ち受けを止める前に呼ぶ。投稿を受け取った相手はその場で Note やアクターの URL を
     * 引きに来るので、止めた後に投稿や配信をすると相手は繋げずに終わる。
     * 送信中の配信は待たない。行は `delivering` のまま残り、次の起動の復旧で送り直される。
     *
     * 何度呼んでもよい。待つのは最初の 1 回だけで、まとめて 3 秒までにする。
     * 同期の DB 呼び出しはキャンセルでは止まらないので、1 つずつ待つと待ちが積み上がり、
     * サーバーの停止（5 秒）と合わせて docker stop の既定の猶予（10 秒）を超える。
     * 超えると DB を閉じる前に殺され、WAL が畳まれない
     */
    fun stopBackgroundWork() {
        if (!backgroundStopped.compareAndSet(false, true)) return
        val jobs = listOf(deliveryScope).map { it.coroutineContext.job }
        jobs.forEach { it.cancel() }
        runBlocking {
            withTimeoutOrNull(3_000) {
                jobs.forEach { it.join() }
            }
        }
    }

    /**
     * 抱えているものを作った順の逆に閉じる。
     *
     * 1 つが投げても残りは閉じ切る。並べて呼ぶだけだと、最初の close が投げた時点で
     * 後ろが開いたままになる。投げられたものは最初の 1 つにまとめて上げ直す。
     */
    override fun close() {
        // 送信の途中で DB や HTTP クライアントを閉じないよう、先に止めて終わるまで待つ
        stopBackgroundWork()

        val failures = listOf<() -> Unit>(
            { delivery.close() },
            { remoteActors.close() },
            { sessionStore.close() },
            { loginFailureStore.close() },
            { repositories.close() },
        ).mapNotNull { close -> runCatching(close).exceptionOrNull() }

        val failure = failures.firstOrNull() ?: return
        failures.drop(1).forEach { failure.addSuppressed(it) }
        throw failure
    }

    companion object {
        /**
         * DB を先に開く。鍵のファイルが無いときに新しく作ってよいかどうかが、
         * フォロワーが記録されているかどうかで決まるため。
         * 開いた後に失敗した場合は、開いた分を閉じてから投げ直す。
         */
        fun create(env: ServerEnv): AppDependencies {
            val repositories = Repositories.create(DatabaseConfig(path = env.dbPath))
            val openTelemetry = OpenTelemetryInitializer.get()

            // ここから先で失敗すると、開いた DB が閉じられないまま起動が止まる
            return runCatching {
                val loadActorKey = ActorKeyLoader.load(env.actorPrivateKey)
                // 送り残した配信は、消したアカウントの Delete{Actor} のようにフォロワーが
                // 1 人も残っていない形でも起きる。新しい鍵で署名すると、相手が覚えている
                // 鍵で検証できずに届かない
                if (loadActorKey == null && (repositories.followers.hasAny() || repositories.deliveryQueue.hasUnsent())) {
                    throw IllegalStateException(
                        "フォロワーか送り残した配信があるのにアクターの秘密鍵が無い。" +
                            "鍵を失った状態で新しい鍵を作ると相手から見て別人になるため起動しない。" +
                            "以前の鍵を ACTOR_PRIVATE_KEY_PATH に戻すこと",
                    )
                }
                val actorKey = loadActorKey ?: when (env.actorPrivateKey) {
                    is ActorPrivateKey.Pem -> throw IllegalStateException()
                    is ActorPrivateKey.File -> ActorKeyLoader.create(env.actorPrivateKey)
                }

                // 相手のアクターを引くのと、こちらから送るのとで外向きの HTTP を張る。
                // どちらも接続を抱えるので、サーバーの外側で開いて確実に閉じる
                val remoteActors = HttpRemoteActors(
                    client = KtorActivityPubHttpClient(openTelemetry),
                    openTelemetry = openTelemetry,
                )

                val delivery =
                    runCatching { HttpActivityDelivery(actorKey, client = KtorActivityPubHttpClient(openTelemetry)) }
                        .getOrElse { failure ->
                            remoteActors.close()
                            throw failure
                        }

                AppDependencies(
                    repositories = repositories,
                    actorKey = actorKey,
                    sessionStore = ValkeyUserSessionStore(uri = env.valkeyUri),
                    loginFailureStore = ValkeyLoginFailureStore(uri = env.valkeyUri),
                    env = env,
                    remoteActors = remoteActors,
                    delivery = delivery,
                )
            }.getOrElse { failure ->
                repositories.close()
                throw failure
            }
        }
    }
}
