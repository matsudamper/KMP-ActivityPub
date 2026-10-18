package net.matsudamper.kmp.activitypub

import java.time.Clock
import kotlin.time.Duration.Companion.days
import net.matsudamper.kmp.activitypub.federation.FollowAcceptSender
import net.matsudamper.kmp.activitypub.federation.OkHttpActivityPubClient
import net.matsudamper.kmp.activitypub.logic.AccountService
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
import net.matsudamper.kotpub.actor.HttpRemoteActors
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.delivery.ActivityDelivery
import net.matsudamper.kotpub.delivery.HttpActivityDelivery

class AppDependencies(
    val repositories: Repositories,
    private val sessionStore: UserSessionStore,
    private val loginFailureStore: LoginFailureStore,
    val remoteActors: RemoteActors,
    private val activityDelivery: ActivityDelivery,
    val env: ServerEnv,
) : AutoCloseable {
    val staticFiles: StaticFiles? = env.staticSrcDir?.let { StaticFiles(it) }?.takeIf { it.isAvailable() }

    val accountService: AccountService = AccountService(accountRepository = repositories.accountRepository)

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

    internal fun followAcceptSender(domain: String): FollowAcceptSender =
        FollowAcceptSender(
            followerRepository = repositories.followerRepository,
            accountService = accountService,
            delivery = activityDelivery,
            domain = domain,
            clock = Clock.systemUTC(),
        )

    override fun close() {
        remoteActors.close()
        activityDelivery.close()
        sessionStore.close()
        loginFailureStore.close()
        repositories.close()
    }

    companion object {
        fun create(env: ServerEnv): AppDependencies =
            AppDependencies(
                repositories = Repositories.create(DatabaseConfig(path = env.dbPath)),
                sessionStore = ValkeyUserSessionStore(uri = env.valkeyUri),
                loginFailureStore = ValkeyLoginFailureStore(uri = env.valkeyUri),
                remoteActors = HttpRemoteActors(client = OkHttpActivityPubClient(), openTelemetry = OpenTelemetryInitializer.get()),
                activityDelivery = HttpActivityDelivery(client = OkHttpActivityPubClient()),
                env = env,
            )
    }
}
