package net.matsudamper.kmp.activitypub.frontend.logic

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.websocket.GraphQLWsProtocol
import com.apollographql.apollo.network.websocket.WebSocketEngine
import com.apollographql.apollo.network.websocket.WebSocketNetworkTransport

object GraphQlClient {
    fun builder(
        serverUrl: String,
        webSocketServerUrl: String,
        webSocketEngine: WebSocketEngine,
    ): ApolloClient.Builder =
        ApolloClient.Builder()
            .serverUrl(serverUrl)
            .subscriptionNetworkTransport(
                WebSocketNetworkTransport.Builder()
                    .serverUrl(webSocketServerUrl)
                    .webSocketEngine(webSocketEngine)
                    .wsProtocol(GraphQLWsProtocol())
                    .build(),
            )
}
