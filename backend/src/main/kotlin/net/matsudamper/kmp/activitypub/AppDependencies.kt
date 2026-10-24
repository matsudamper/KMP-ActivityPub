package net.matsudamper.kmp.activitypub

import java.time.Clock
import kotlin.time.Duration.Companion.days
import net.matsudamper.kmp.activitypub.federation.Federation
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.logic.UserSearchService
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
) : AutoCloseable {
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
            accountService = accountService,
            remoteUserLookup = federation?.remoteUserLookup,
            localDomain = env.domain,
        )

    val sessionService: UserSessionService = UserSessionService(
        sessionStore = sessionStore,
        ttl = 30.days,
        clock = Clock.systemUTC(),
    )

    val passkeyService: PasskeyService =
        PasskeyService(
            relyingParty = env.passkeyRelyingParty,
            passkeyRepository = repositories.passkeyRepository,
            challengeStore = passkeyChallengeStore,
            clock = Clock.systemUTC(),
        )
    val loginThrottle: LoginThrottle = LoginThrottle(
        failureStore = loginFailureStore,
        maxFailures = 5,
        lockDuration = 30.days,
    )

    override fun close() {
        passkeyChallengeStore.close()
        federation?.close()
        sessionStore.close()
        loginFailureStore.close()
        repositories.close()
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
