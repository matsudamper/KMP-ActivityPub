package net.matsudamper.kmp.activitypub

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ServerReady
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.opentelemetry.instrumentation.ktor.v3_0.KtorServerTelemetry
import net.matsudamper.kmp.activitypub.federation.federationRoutes
import net.matsudamper.kmp.activitypub.follower.followerRoutes
import net.matsudamper.kmp.activitypub.graphql.DiContainer
import net.matsudamper.kmp.activitypub.graphql.GraphQlContext
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.graphQlRoutes
import net.matsudamper.kmp.activitypub.graphql.resolver.AdminMutationResolverImpl
import net.matsudamper.kmp.activitypub.graphql.resolver.AdminQueryResolverImpl
import net.matsudamper.kmp.activitypub.graphql.resolver.MutationResolverImpl
import net.matsudamper.kmp.activitypub.graphql.resolver.QueryResolverImpl
import net.matsudamper.kmp.activitypub.graphql.resolver.UserMutationResolverImpl
import net.matsudamper.kmp.activitypub.inbox.inboxRoutes
import net.matsudamper.kmp.activitypub.nodeinfo.nodeInfoRoutes
import net.matsudamper.kmp.activitypub.note.featuredRoutes
import net.matsudamper.kmp.activitypub.note.noteRoutes
import net.matsudamper.kmp.activitypub.note.outboxRoutes
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
        }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            // 待ち受けを止める前に配信を止める。投稿を受け取った相手はその場で
            // Note やアクターの URL を引きに来るので、止めた後に送ると繋げずに終わる。
            // 送信中の配信は待たない。行は delivering のまま残り、次の起動で送り直される
            deps.federation?.stopBackgroundWork()
            server.stop(gracePeriodMillis = 1_000, timeoutMillis = 5_000)
            deps.close()
        },
    )

    // 受け付けが始まってから動かす。投稿を受け取った相手はその場で Note やアクターの
    // URL を引きに来るので、待ち受ける前に送ると相手は繋げずに終わる
    server.monitor.subscribe(ServerReady) { application ->
        val purgedAccounts = deps.federation?.startDeliveryWorker() ?: 0
        if (purgedAccounts > 0) {
            application.log.info("配信を送り切った削除済みアカウント $purgedAccounts 件を片付けた")
        }
    }

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

    if (deps.env.adminPasswordHash == null) {
        log.warn("ADMIN_PASSWORD_HASH が未設定なので管理画面にログインできない")
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
                AdminQueryResolverImpl(),
                AdminMutationResolverImpl(),
                UserMutationResolverImpl(),
            ),
            createContext = { call ->
                GraphQlContext(
                    call = call,
                    adminSessionStore = deps.adminSessionStore,
                    sessionService = deps.sessionService,
                    cookieSecure = deps.env.cookieSecure,
                    trustCfConnectingIp = deps.env.trustCfConnectingIp,
                )
            },
            diContainer = DiContainer(
                passwordHash = deps.env.adminPasswordHash,
                accountService = deps.accountService,
                loginThrottle = deps.loginThrottle,
                localDomain = localDomain,
                federation = deps.federation,
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
            federationRoutes(accountService = deps.accountService, localDomain = localDomain)
        }
        deps.federation?.let { activityPubRoutes(it) }
        staticRoutes(staticFiles)
    }
}

private fun Route.activityPubRoutes(federation: FederationDependencies) {
    // フォローなどのアクティビティはここに POST されてくる
    inboxRoutes(directory = federation.directory, service = federation.inboxService)

    followerRoutes(federation.directory, federation.followerStore)
    outboxRoutes(federation.directory, federation.noteStore, federation.webPageUrls)
    featuredRoutes(federation.directory)
    noteRoutes(federation.domain, federation.noteStore, federation.webPageUrls)

    nodeInfoRoutes(federation.domain)
}
