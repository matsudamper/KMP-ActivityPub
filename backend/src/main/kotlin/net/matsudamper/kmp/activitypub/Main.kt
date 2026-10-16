package net.matsudamper.kmp.activitypub

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.GraphQlContext
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.graphQlRoutes
import net.matsudamper.kmp.activitypub.graphql.resolver.MutationResolverImpl
import net.matsudamper.kmp.activitypub.graphql.resolver.QueryResolverImpl
import net.matsudamper.kmp.activitypub.graphql.resolver.UserMutationResolverImpl
import net.matsudamper.kmp.activitypub.repository.DatabaseWriteCheck
import net.matsudamper.kmp.activitypub.staticfiles.staticRoutes

fun main() {
    val serverEnv = ServerEnv()
    DatabaseWriteCheck.verify(serverEnv.dbPath)
    val deps = AppDependencies.create(serverEnv)

    val server =
        embeddedServer(CIO, port = serverEnv.port, host = serverEnv.host) {
            module(deps)
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
    val staticFiles = deps.staticFiles
    if (staticFiles != null) {
        log.info("静的ファイルの配信元: ${staticFiles.root}")
    } else {
        log.info(
            "STATIC_SRC_DIR が未設定またはディレクトリが存在しないため、静的ファイルを配信しない。" +
                "画面を出すには画面の配布物を置いたディレクトリを指定する",
        )
    }

    val passkeyRelyingParty = deps.env.passkeyRelyingParty
    if (passkeyRelyingParty != null) {
        log.info("パスキーの RP ID: ${passkeyRelyingParty.id} origin: ${passkeyRelyingParty.origins.joinToString()}")
    } else {
        log.info("PASSKEY_RP_ID が未設定のため、パスキーを使わない")
    }

    if (!deps.env.cookieSecure) {
        log.warn("COOKIE_SECURE=false なのでセッション Cookie に Secure を付けない。http で試すとき以外は外すこと")
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
                )
            },
            diContainer = DiContainer(
                accountService = deps.accountService,
                passkeyService = deps.passkeyService,
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
        staticRoutes(staticFiles)
    }
}
