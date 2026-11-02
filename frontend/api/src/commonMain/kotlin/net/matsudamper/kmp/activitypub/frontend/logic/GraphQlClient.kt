package net.matsudamper.kmp.activitypub.frontend.logic

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.NetworkTransport
import com.apollographql.apollo.network.websocket.GraphQLWsProtocol
import com.apollographql.apollo.network.websocket.WebSocketEngine
import com.apollographql.apollo.network.websocket.WebSocketNetworkTransport
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import net.matsudamper.kmp.activitypub.frontend.graphql.cache.Cache.cache

object GraphQlClient {
    private const val CACHE_MAX_SIZE_BYTES = 10 * 1024 * 1024

    fun builder(
        serverUrl: String,
        subscriptionNetworkTransport: NetworkTransport,
    ): ApolloClient.Builder =
        ApolloClient.Builder()
            .serverUrl(serverUrl)
            .subscriptionNetworkTransport(subscriptionNetworkTransport)
            .cache(MemoryCacheFactory(maxSizeBytes = CACHE_MAX_SIZE_BYTES))
            // キャッシュを使うのはページングの watch だけ。既定の CacheFirst のままだと、操作後の読み直しで古い内容が返る
            .fetchPolicy(FetchPolicy.NetworkOnly)

    fun webSocketNetworkTransport(
        serverUrl: String,
        webSocketEngine: WebSocketEngine,
    ): NetworkTransport =
        WebSocketNetworkTransport.Builder()
            .serverUrl(serverUrl)
            .webSocketEngine(webSocketEngine)
            .wsProtocol(GraphQLWsProtocol())
            .build()
}
