package net.matsudamper.kmp.activitypub.android

import java.io.Closeable
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Protocol
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject

internal class LoginTestServer : Closeable {
    val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
    val logins = AtomicInteger()
    val subscriptions = AtomicInteger()
    private val server = MockWebServer().apply {
        useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory())
        protocols = listOf(Protocol.HTTP_1_1)
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.headers["Upgrade"].equals("websocket", ignoreCase = true)) {
                    check(request.headers["Cookie"] == "user_session=test-session")
                    return MockResponse.Builder().setHeader("Sec-WebSocket-Protocol", "graphql-transport-ws")
                        .webSocketUpgrade(object : WebSocketListener() {
                            override fun onMessage(webSocket: WebSocket, text: String) {
                                val message = JSONObject(text)
                                when (message.getString("type")) {
                                    "connection_init" -> webSocket.send("""{"type":"connection_ack"}""")
                                    "subscribe" -> subscriptions.incrementAndGet()
                                }
                            }
                        }).build()
                }
                val operation = JSONObject(requireNotNull(request.body).utf8()).getString("operationName")
                val loggedIn = request.headers["Cookie"] == "user_session=test-session"
                val response = when (operation) {
                    "UserLogin" -> {
                        val variables = JSONObject(requireNotNull(request.body).utf8()).getJSONObject("variables").getJSONObject("query")
                        check(variables.getString("username") == "alice")
                        check(variables.getString("password") == "password")
                        logins.incrementAndGet()
                        MockResponse.Builder()
                            .setHeader("Set-Cookie", "user_session=test-session; Path=/; Max-Age=3600; Secure; HttpOnly")
                            .body("""{"data":{"user":{"__typename":"UserMutation","login":{"__typename":"UserLoginResult","failure":null}}}}""")
                    }

                    "LoginGate" -> MockResponse.Builder().body(
                        if (loggedIn) {
                            """{"data":{"userInfo":{"__typename":"UserInfo","id":"alice-id"}}}"""
                        } else {
                            """{"data":{"userInfo":null}}"""
                        },
                    )

                    "MyProfile" -> MockResponse.Builder().body(
                        """{"data":{"profile":{"__typename":"Profile","username":"alice","displayName":null,"iconUrl":null,"followingCount":0,"followersCount":0}}}""",
                    )

                    "HomeTimeline" -> {
                        check(loggedIn)
                        MockResponse.Builder().body(
                            """{"data":{"userInfo":{"__typename":"UserInfo","homeTimeline":{"__typename":"HomeTimeline","posts":[{"__typename":"HomeTimelinePost","id":"post-a","content":"ログイン後のテスト投稿 https://example.com/","publishedAt":"2026-11-03T10:00:00Z","username":"alice","host":null,"displayName":"Alice","summary":null,"sensitive":false,"linkUrls":[],"iconUrl":null,"images":[]}],"nextCursor":null}}}}""",
                        )
                    }

                    else -> error("想定外の操作: $operation")
                }
                return response.setHeader("Content-Type", "application/json").build()
            }
        }
        start()
    }
    val origin: String = server.url("/").toString().removeSuffix("/")

    override fun close() = server.close()
}
