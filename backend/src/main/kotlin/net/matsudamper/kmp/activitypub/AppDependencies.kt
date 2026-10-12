package net.matsudamper.kmp.activitypub

import java.time.Clock
import kotlin.time.Duration.Companion.days
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.UserSessionService
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.repository.createRepositories
import net.matsudamper.kmp.activitypub.staticfiles.StaticFiles

/**
 * 外から作れるようにしてあるのはテストのため。本番の組み立ては [create] にある。
 */
class AppDependencies(
    val repositories: Repositories,
    val env: ServerEnv,
) : AutoCloseable {
    val staticFiles: StaticFiles? = env.staticSrcDir?.let { StaticFiles(it) }?.takeIf { it.isAvailable() }

    val accountService: AccountService = AccountService(accounts = repositories.accounts)

    val userSessions: UserSessionService = UserSessionService(
        sessions = repositories.userSessions,
        ttl = 30.days,
        clock = Clock.systemUTC(),
    )

    override fun close() {
        repositories.close()
    }

    companion object {
        fun create(env: ServerEnv): AppDependencies =
            AppDependencies(
                repositories = createRepositories(DatabaseConfig(path = env.dbPath)),
                env = env,
            )
    }
}
