package net.matsudamper.kmp.activitypub

import kotlinx.coroutines.launch
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import net.matsudamper.kmp.activitypub.federation.federationRoutes
import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.GraphQlContext
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.graphQlRoutes
import net.matsudamper.kmp.activitypub.graphql.resolver.MutationResolverImpl
import net.matsudamper.kmp.activitypub.graphql.resolver.QueryResolverImpl
import net.matsudamper.kmp.activitypub.graphql.resolver.UserMutationResolverImpl
import net.matsudamper.kmp.activitypub.repository.DatabaseWriteCheck
import net.matsudamper.kmp.activitypub.staticfiles.staticRoutes
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.TraceLogger

fun main() {
    val serverEnv = ServerEnv()
    OpenTelemetryInitializer.initialize()
    DatabaseWriteCheck.verify(serverEnv.dbPath)
    val deps = AppDependencies.create(serverEnv)

    val server =
        embeddedServer(CIO, port = serverEnv.port, host = serverEnv.host) {
            module(deps)
            // テストでは送るタイミングを決めたいので module の外で動かす
            val domain = serverEnv.domain
            if (domain != null) {
                val followAcceptSender = deps.followAcceptSender(domain)
                launch { followAcceptSender.run() }
            }
        }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
            deps.close()
        },
    )

    server.start(wait = true)
}

internal fun Application.module(deps: AppDependencies) {
    install(KtorServerTelemetry) {
        setOpenTelemetry(OpenTelemetryInitializer.get())
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            TraceLogger.impl().noticeThrowable(cause, isError = true)
            call.respondText(
                status = HttpStatusCode.InternalServerError,
                text = HttpStatusCode.InternalServerError.description,
            )
        }
    }

    val staticFiles = deps.staticFiles
    if (staticFiles != null) {
        log.info("静的ファイルの配信元: ${staticFiles.root}")
    } else {
        log.info(
            "STATIC_SRC_DIR が未設定またはディレクトリが存在しないため、静的ファイルを配信しない。" +
                "画面を出すには画面の配布物を置いたディレクトリを指定する",
        )
    }

    if (!deps.env.cookieSecure) {
        log.warn("COOKIE_SECURE=false なのでセッション Cookie に Secure を付けない。http で試すとき以外は外すこと")
    }

    val localDomain = deps.env.domain
    if (localDomain == null) {
        log.info("DOMAIN が未設定なので、他のサーバーから見つけられない")
    }

    val graphQl =
        GraphQlEngine.create(
            resolvers = listOf(
                QueryResolverImpl(),
                MutationResolverImpl(),
                UserMutationResolverImpl(),
            ),
            createContext = { call ->
                GraphQlContext(
                    call = call,
                    sessionService = deps.sessionService,
                    cookieSecure = deps.env.cookieSecure,
                    trustCfConnectingIp = deps.env.trustCfConnectingIp,
                )
            },
            diContainer = DiContainer(
                accountService = deps.accountService,
                loginThrottle = deps.loginThrottle,
            ),
        )

    routing {
        get("/healthz") {
            call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
        }
        get("/hello") {
            call.respondText("Hello World")
        }
        graphQlRoutes(graphQl)
        if (localDomain != null) {
            federationRoutes(
                accountService = deps.accountService,
                followerRepository = deps.repositories.followerRepository,
                remoteActors = deps.remoteActors,
                localDomain = localDomain,
            )
        }
        staticRoutes(staticFiles)
    }
}
