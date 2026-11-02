package net.matsudamper.kmp.activitypub.frontend.logic

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.network.websocket.WebSocketNetworkTransport
import com.apollographql.apollo.network.websocket.closeConnection

/**
 * サーバーは WebSocket を張ったときの Cookie でセッションを見るので、セッションが変わったら張り直させる。
 * 開いたままだと、購読し直しても前のセッションで断られ続ける
 */
internal fun ApolloClient.closeSubscriptionConnection() {
    val transport = subscriptionNetworkTransport
    if (transport is WebSocketNetworkTransport) transport.closeConnection()
}
