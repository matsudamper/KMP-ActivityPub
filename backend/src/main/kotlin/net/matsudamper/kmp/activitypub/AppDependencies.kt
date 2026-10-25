package net.matsudamper.kmp.activitypub

import java.time.Clock
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.days
import net.matsudamper.kmp.activitypub.federation.Federation
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.imagestorage.s3.S3ImageStorage
import net.matsudamper.kmp.activitypub.imagestorage.s3.StsCredentialProvider
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.logic.UserSearchService
import net.matsudamper.kmp.activitypub.oidc.OidcSigningKey
import net.matsudamper.kmp.activitypub.oidc.WebIdentityTokenIssuer
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.session.LoginFailureStore
import net.matsudamper.kmp.activitypub.session.LoginThrottle
import net.matsudamper.kmp.activitypub.session.PasskeyChallengeStore
import net.matsudamper.kmp.activitypub.session.UserSessionService
import net.matsudamper.kmp.activitypub.session.UserSessionStore
import net.matsudamper.kmp.activitypub.session.ValkeyLoginFailureStore
import net.matsudamper.kmp.activitypub.session.ValkeyPasskeyChallengeStore
import net.matsudamper.kmp.activitypub.session.ValkeyUserSessionStore
import net.matsudamper.kmp.activitypub.staticfiles.StaticFiles
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer

class AppDependencies(
    val repositories: Repositories,
    private val sessionStore: UserSessionStore,
    private val passkeyChallengeStore: PasskeyChallengeStore,
    private val loginFailureStore: LoginFailureStore,
    val env: ServerEnv,
) {
    val staticFiles: StaticFiles? = env.staticSrcDir?.let { StaticFiles(it) }?.takeIf { it.isAvailable() }

    val accountService: AccountService = AccountService(accountRepository = repositories.accountRepository)

    /**
     * DOMAIN が無いと他のサーバーから見つけられないので作らない
     */
    internal val federation: Federation? =
        env.domain?.let { domain ->
            Federation(
                domain = domain,
                accountService = accountService,
                followerRepository = repositories.followerRepository,
                deliveryQueueRepository = repositories.deliveryQueueRepository,
                noteRepository = repositories.noteRepository,
                openTelemetry = OpenTelemetryInitializer.get(),
            )
        }

    val userSearchService: UserSearchService =
        UserSearchService(
            accountRepository = repositories.accountRepository,
            remoteActorRepository = repositories.remoteActorRepository,
            remoteUserLookup = federation?.remoteUserLookup,
            localDomain = checkNotNull(env.domain) { "DOMAIN を指定すること" },
        )

    val sessionService: UserSessionService = UserSessionService(
        sessionStore = sessionStore,
        ttl = 30.days,
        clock = Clock.systemUTC(),
    )

    /**
     * ENABLE_PASSKEY が false なら作らない
     */
    val passkeyService: PasskeyService? =
        env.passkeyRelyingParty?.let { relyingParty ->
            PasskeyService(
                relyingParty = relyingParty,
                passkeyRepository = repositories.passkeyRepository,
                challengeStore = passkeyChallengeStore,
                clock = Clock.systemUTC(),
            )
        }
    val loginThrottle: LoginThrottle = LoginThrottle(
        failureStore = loginFailureStore,
        maxFailures = 5,
        lockDuration = 30.days,
    )

    val oidcSigningKey: OidcSigningKey? = env.s3?.let { OidcSigningKey(it.oidc.privateJwkJson) }

    private val webIdentityTokenIssuer: WebIdentityTokenIssuer? =
        env.s3?.let { s3 ->
            WebIdentityTokenIssuer(
                signingKey = checkNotNull(oidcSigningKey),
                issuer = s3.oidc.issuer,
                clock = Clock.systemUTC(),
            )
        }

    /**
     * ENABLE_S3 が false なら画像を置けない
     */
    val imageStorage: ImageStorage? =
        env.s3?.let { s3 ->
            S3ImageStorage(
                stsCredentialProvider = StsCredentialProvider(
                    tokenIssuer = checkNotNull(webIdentityTokenIssuer),
                    config = s3.imageStorage,
                ),
                config = s3.imageStorage,
            )
        }

    /**
     * 順に閉じると、どれかの close が返らないときに後ろが閉じられない。
     * SQLite の close が走らないと -wal が畳まれずに残る
     */
    fun closeIndependently() {
        listOfNotNull(passkeyChallengeStore, federation, sessionStore, loginFailureStore, repositories)
            .map { closeable -> thread { closeable.close() } }
            .forEach { it.join() }
    }

    companion object {
        fun create(env: ServerEnv): AppDependencies =
            AppDependencies(
                repositories = Repositories.create(DatabaseConfig(path = env.dbPath)),
                sessionStore = ValkeyUserSessionStore(uri = env.valkeyUri),
                passkeyChallengeStore = ValkeyPasskeyChallengeStore(uri = env.valkeyUri),
                loginFailureStore = ValkeyLoginFailureStore(uri = env.valkeyUri),
                env = env,
            )
    }
}
