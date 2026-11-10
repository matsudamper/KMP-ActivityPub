package net.matsudamper.kmp.activitypub.android

import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject

internal class LoginTestServer : Closeable {
    val certificate = HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
    val logins = AtomicInteger()
    val subscriptions = AtomicInteger()
    val openedConnections = AtomicInteger()
    val closedConnections = AtomicInteger()
    val connectionEvents = ConcurrentLinkedQueue<String>()
    private val activeSubscriptions = ConcurrentHashMap<WebSocket, String>()
    private val server = MockWebServer().apply {
        useHttps(HandshakeCertificates.Builder().heldCertificate(certificate).build().sslSocketFactory())
        protocols = listOf(Protocol.HTTP_1_1)
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.headers["Upgrade"].equals("websocket", ignoreCase = true)) {
                    check(request.headers["Cookie"] == "user_session=test-session")
                    return MockResponse.Builder().setHeader("Sec-WebSocket-Protocol", "graphql-transport-ws")
                        .webSocketUpgrade(object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                openedConnections.incrementAndGet()
                                connectionEvents.add("接続")
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                val message = JSONObject(text)
                                when (message.getString("type")) {
                                    "connection_init" -> webSocket.send("""{"type":"connection_ack"}""")

                                    "subscribe" -> {
                                        activeSubscriptions[webSocket] = message.getString("id")
                                        subscriptions.incrementAndGet()
                                        connectionEvents.add("購読開始:${message.getString("id")}")
                                    }

                                    "complete" -> {
                                        activeSubscriptions.remove(webSocket)
                                        connectionEvents.add("購読終了:${message.getString("id")}")
                                    }
                                }
                            }

                            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                                activeSubscriptions.remove(webSocket)
                                connectionEvents.add("切断要求:$code:$reason")
                                webSocket.close(code, reason)
                            }

                            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                activeSubscriptions.remove(webSocket)
                                closedConnections.incrementAndGet()
                                connectionEvents.add("切断完了:$code:$reason")
                            }

                            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                                activeSubscriptions.remove(webSocket)
                                connectionEvents.add("接続失敗:${t.message}")
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
                        """{"data":{"profile":{"__typename":"Profile","username":"alice","displayName":null,"iconUrl":null,"summary":null,"links":[],"followingCount":0,"followersCount":0}}}""",
                    )

                    "HomeTimeline" -> {
                        check(loggedIn)
                        MockResponse.Builder().body(
                            """{"data":{"userInfo":{"__typename":"UserInfo","homeTimeline":{"__typename":"HomeTimeline","posts":[{"__typename":"HomeTimelinePost","id":"post-a","content":"ログイン後のテスト投稿 https://example.com/","publishedAt":"2026-11-03T10:00:00Z","username":"alice","host":null,"displayName":"Alice","displayNameEmojis":[],"summary":null,"sensitive":false,"linkUrls":[],"iconUrl":null,"images":[],"emojis":[],"favouriteCount":null,"boostCount":null,"boostedBy":null,"stamps":[],"url":null,"likedByMe":false,"boostedByMe":null}],"nextCursor":null}}}}""",
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

    fun publishPostAfterRecreation() {
        check(activeSubscriptions.isNotEmpty()) { "購読が残っていない: $connectionEvents" }
        activeSubscriptions.forEach { (webSocket, id) ->
            val subscriptionId = JSONObject.quote(id)
            check(
                webSocket.send(
                    """{"type":"next","id":$subscriptionId,"payload":{"data":{"homeTimelinePosts":{"__typename":"HomeTimelinePost","id":"post-after-recreation","content":"Activity 再生成後の新着投稿","publishedAt":"2026-11-03T10:01:00Z","username":"alice","host":null,"displayName":"Alice","displayNameEmojis":[],"summary":null,"sensitive":false,"linkUrls":[],"iconUrl":null,"images":[],"emojis":[],"favouriteCount":null,"boostCount":null,"boostedBy":null,"stamps":[],"url":null,"likedByMe":false,"boostedByMe":null}}}}""",
                ),
            ) { "新着投稿を送信できない: $connectionEvents" }
        }
    }

    override fun close() = server.close()
}
