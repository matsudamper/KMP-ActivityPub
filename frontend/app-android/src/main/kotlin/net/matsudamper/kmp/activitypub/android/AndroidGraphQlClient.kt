package net.matsudamper.kmp.activitypub.android

import com.apollographql.apollo.ApolloClient
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH

internal object AndroidGraphQlClient {
    /**
     * @param serverUrl `https://example.com` の形
     */
    fun create(serverUrl: String): ApolloClient = GraphQlClient.builder("$serverUrl$GRAPHQL_PATH").build()
}
