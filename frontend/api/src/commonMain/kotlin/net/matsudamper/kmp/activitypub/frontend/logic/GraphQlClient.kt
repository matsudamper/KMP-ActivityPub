package net.matsudamper.kmp.activitypub.frontend.logic

import com.apollographql.apollo.ApolloClient
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH

object GraphQlClient {
    /**
     * ブラウザ用。画面を配信しているサーバーと同じオリジンに投げる
     */
    val apollo: ApolloClient by lazy { builder(GRAPHQL_PATH).build() }

    /**
     * @param serverUrl GraphQL の口の URL。画面と別のオリジンから使う場合は絶対 URL を渡す
     */
    fun builder(serverUrl: String): ApolloClient.Builder =
        ApolloClient
            .Builder()
            .serverUrl(serverUrl)
}
