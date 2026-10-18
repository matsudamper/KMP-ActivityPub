package net.matsudamper.kmp.activitypub

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
import net.matsudamper.kmp.activitypub.delivery.DeliveryCircuitBreaker
import net.matsudamper.kmp.activitypub.delivery.DeliveryRetryPolicy
import net.matsudamper.kmp.activitypub.delivery.DeliveryWorker
import net.matsudamper.kmp.activitypub.delivery.LocalActorKeys
import net.matsudamper.kmp.activitypub.delivery.RepositoryLocalActorKeys
import net.matsudamper.kmp.activitypub.federation.AccountActorNames
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.DomainBlockService
import net.matsudamper.kmp.activitypub.logic.NoteEnqueuer
import net.matsudamper.kmp.activitypub.logic.RepositoryEarlyUndoneLikes
import net.matsudamper.kmp.activitypub.logic.RepositoryFavouriteStore
import net.matsudamper.kmp.activitypub.logic.RepositoryFollowerStore
import net.matsudamper.kmp.activitypub.logic.RepositoryNoteStore
import net.matsudamper.kmp.activitypub.logic.RepositoryStampStore
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.delivery.ActivityDelivery
import net.matsudamper.kotpub.favourite.FavouriteStore
import net.matsudamper.kotpub.follower.FollowerStore
import net.matsudamper.kotpub.inbox.InboxDomainBlocks
import net.matsudamper.kotpub.inbox.InboxService
import net.matsudamper.kotpub.note.FollowBackfillPublisher
import net.matsudamper.kotpub.note.NotePublisher
import net.matsudamper.kotpub.note.NoteStore
import net.matsudamper.kotpub.url.WebPageUrls

/**
 * 他のサーバーとやり取りするためのもの。`DOMAIN` が決まっているときだけ作る。
 *
 * @param remoteActors 相手のアクターの引き先。署名検証に使う公開鍵と、
 *   `Accept` の宛先になる inbox をここから取る
 */
class FederationDependencies(
    private val repositories: Repositories,
    val domain: String,
    accountService: AccountService,
    val remoteActors: RemoteActors,
    private val delivery: ActivityDelivery,
    private val actorKeys: LocalActorKeys = RepositoryLocalActorKeys(repositories.accountRepository),
) : AutoCloseable {
    /**
     * 相手に渡す画面の URL。アカウントと投稿の画面はまだ無いので出さない。
     *
     * 出さなければ相手は `id` に倒すので、開けないページを指すより JSON のパスが開く方がまだ読める
     */
    val webPageUrls: WebPageUrls? = null

    val followerStore: FollowerStore = RepositoryFollowerStore(repositories.followers)

    val noteStore: NoteStore = RepositoryNoteStore(repositories.notes)

    private val favouriteStore: FavouriteStore = RepositoryFavouriteStore(repositories.noteFavourites)

    val directory: ActorDirectory = ActorDirectory(domain = domain, stored = AccountActorNames(accountService))

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
        domain = domain,
        stored = object : StoredActorNames {
            override fun find(username: String): String? = repositories.accountRepository.findDeletedByUsername(username)?.username

            override fun finds(usernames: Set<String>): Map<String, String> {
                return usernames.mapNotNull { username -> find(username)?.let { username to it } }.toMap()
            }
        },
    )

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
        domain = domain,
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
            actorKeys = actorKeys,
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

    override fun close() {
        // 送信の途中で HTTP クライアントを閉じないよう、先に止めて終わるまで待つ
        stopBackgroundWork()

        val failures = listOf<() -> Unit>(
            { delivery.close() },
            { remoteActors.close() },
        ).mapNotNull { close -> runCatching(close).exceptionOrNull() }

        val failure = failures.firstOrNull() ?: return
        failures.drop(1).forEach { failure.addSuppressed(it) }
        throw failure
    }
}
