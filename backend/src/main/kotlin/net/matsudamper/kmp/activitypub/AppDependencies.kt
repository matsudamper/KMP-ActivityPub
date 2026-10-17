package net.matsudamper.kmp.activitypub

import java.time.Clock
import kotlin.time.Duration.Companion.days
import io.opentelemetry.api.OpenTelemetry
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.session.UserSessionService
import net.matsudamper.kmp.activitypub.session.UserSessionStore
import net.matsudamper.kmp.activitypub.session.ValkeyUserSessionStore
import net.matsudamper.kmp.activitypub.staticfiles.StaticFiles

class AppDependencies(
    val repositories: Repositories,
    private val sessionStore: UserSessionStore,
    val env: ServerEnv,
    val openTelemetry: OpenTelemetry = OpenTelemetry.noop(),
) : AutoCloseable {
    val staticFiles: StaticFiles? = env.staticSrcDir?.let { StaticFiles(it) }?.takeIf { it.isAvailable() }

    val accountService: AccountService = AccountService(accountRepository = repositories.accountRepository)

    val sessionService: UserSessionService = UserSessionService(
        sessionStore = sessionStore,
        ttl = 30.days,
        clock = Clock.systemUTC(),
    )

    override fun close() {
        sessionStore.close()
        repositories.close()
    }

    companion object {
        fun create(
            env: ServerEnv,
            openTelemetry: OpenTelemetry,
        ): AppDependencies =
            AppDependencies(
                repositories = Repositories.create(
                    config = DatabaseConfig(path = env.dbPath),
                    openTelemetry = openTelemetry,
                ),
                sessionStore = ValkeyUserSessionStore(uri = env.valkeyUri, openTelemetry = openTelemetry),
                env = env,
                openTelemetry = openTelemetry,
            )
    }
}
