package net.matsudamper.kmp.activitypub

import java.time.Clock
import kotlin.time.Duration.Companion.days
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.session.UserSessionService
import net.matsudamper.kmp.activitypub.staticfiles.StaticFiles

class AppDependencies(
    val repositories: Repositories,
    val env: ServerEnv,
) : AutoCloseable {
    val staticFiles: StaticFiles? = env.staticSrcDir?.let { StaticFiles(it) }?.takeIf { it.isAvailable() }

    val accountService: AccountService = AccountService(accountRepository = repositories.accountRepository)

    val sessionService: UserSessionService = UserSessionService(
        sessionStore = repositories.sessionStore,
        ttl = 30.days,
        clock = Clock.systemUTC(),
    )

    val passkeyService: PasskeyService? =
        env.passkeyRelyingParty?.let { relyingParty ->
            PasskeyService(
                relyingParty = relyingParty,
                passkeyRepository = repositories.passkeyRepository,
                challengeStore = repositories.passkeyChallengeStore,
                clock = Clock.systemUTC(),
            )
        }

    override fun close() {
        repositories.close()
    }

    companion object {
        fun create(env: ServerEnv): AppDependencies =
            AppDependencies(
                repositories = Repositories.create(DatabaseConfig(path = env.dbPath)),
                env = env,
            )
    }
}
