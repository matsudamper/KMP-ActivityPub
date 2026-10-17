package net.matsudamper.kmp.activitypub

import java.time.Clock
import kotlin.time.Duration.Companion.days
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

class AppDependencies(
    val repositories: Repositories,
    private val sessionStore: UserSessionStore,
    private val loginFailureStore: LoginFailureStore,
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

    override fun close() {
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
                env = env,
            )
    }
}
