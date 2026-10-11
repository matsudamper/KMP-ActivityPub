package net.matsudamper.kmp.activitypub

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import net.matsudamper.kmp.activitypub.staticfiles.StaticFiles
import net.matsudamper.kmp.activitypub.staticfiles.staticRoutes

fun main() {
    val serverEnv = ServerEnv()
    val staticFiles = serverEnv.staticSrcDir?.let { StaticFiles(it) }?.takeIf { it.isAvailable() }

    val server =
        embeddedServer(CIO, port = serverEnv.port, host = serverEnv.host) {
            module(staticFiles = staticFiles)
        }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            server.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
        },
    )

    server.start(wait = true)
}

internal fun Application.module(staticFiles: StaticFiles?) {
    if (staticFiles != null) {
        log.info("静的ファイルの配信元: ${staticFiles.root}")
    } else {
        log.info(
            "STATIC_SRC_DIR が未設定またはディレクトリが存在しないため、静的ファイルを配信しない。" +
                "画面を出すには画面の配布物を置いたディレクトリを指定する",
        )
    }

    routing {
        get("/healthz") {
            call.respondText("""{"status":"ok"}""", ContentType.Application.Json)
        }
        get("/hello") {
            call.respondText("Hello World")
        }
        staticRoutes(staticFiles)
    }
}
