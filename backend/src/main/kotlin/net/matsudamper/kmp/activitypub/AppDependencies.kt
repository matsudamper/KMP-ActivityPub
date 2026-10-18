package net.matsudamper.kmp.activitypub

import java.time.Clock
import kotlin.time.Duration.Companion.days
import net.matsudamper.kmp.activitypub.admin.AdminSessionInMemoryStore
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
import net.matsudamper.kotpub.delivery.HttpActivityDelivery
import net.matsudamper.kotpub.http.KtorActivityPubHttpClient
import net.matsudamper.kotpub.httpsignature.HttpSignatureSigner

/**
 * 外から作れるようにしてあるのはテストのため。本番の組み立ては [create] にある。
 *
 * @param createFederation `DOMAIN` が決まっているときだけ呼ぶ
 */
class AppDependencies(
    val repositories: Repositories,
    private val sessionStore: UserSessionStore,
    private val loginFailureStore: LoginFailureStore,
    val env: ServerEnv,
    val adminSessionStore: AdminSessionInMemoryStore = AdminSessionInMemoryStore(),
    createFederation: (domain: String, accountService: AccountService) -> FederationDependencies,
) : AutoCloseable {
    val staticFiles: StaticFiles? = env.staticSrcDir?.let { StaticFiles(it) }?.takeIf { it.isAvailable() }

    val accountService: AccountService = AccountService(accountRepository = repositories.accountRepository)

    val federation: FederationDependencies? = env.domain?.let { createFederation(it, accountService) }

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

    /**
     * 1 つが投げても残りは閉じ切る。並べて呼ぶだけだと、最初の close が投げた時点で
     * 後ろが開いたままになる。投げられたものは最初の 1 つにまとめて上げ直す。
     */
    override fun close() {
        val failures = listOf<() -> Unit>(
            { federation?.close() },
            { sessionStore.close() },
            { loginFailureStore.close() },
            { repositories.close() },
        ).mapNotNull { close -> runCatching(close).exceptionOrNull() }

        val failure = failures.firstOrNull() ?: return
        failures.drop(1).forEach { failure.addSuppressed(it) }
        throw failure
    }

    companion object {
        fun create(env: ServerEnv): AppDependencies {
            val repositories = Repositories.create(DatabaseConfig(path = env.dbPath))
            return AppDependencies(
                repositories = repositories,
                sessionStore = ValkeyUserSessionStore(uri = env.valkeyUri),
                loginFailureStore = ValkeyLoginFailureStore(uri = env.valkeyUri),
                env = env,
                createFederation = { domain, accountService ->
                    val openTelemetry = OpenTelemetryInitializer.get()
                    // 相手のアクターを引くのと、こちらから送るのとで外向きの HTTP を張る。
                    // どちらも接続を抱えるので、サーバーの外側で開いて確実に閉じる
                    FederationDependencies(
                        repositories = repositories,
                        domain = domain,
                        accountService = accountService,
                        remoteActors = HttpRemoteActors(
                            client = KtorActivityPubHttpClient(openTelemetry),
                            openTelemetry = openTelemetry,
                        ),
                        delivery = HttpActivityDelivery(
                            client = KtorActivityPubHttpClient(openTelemetry),
                            signer = HttpSignatureSigner(),
                        ),
                    )
                },
            )
        }
    }
}
