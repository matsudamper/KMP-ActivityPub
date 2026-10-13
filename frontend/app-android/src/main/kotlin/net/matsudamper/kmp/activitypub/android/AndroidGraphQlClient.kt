package net.matsudamper.kmp.activitypub.android

import com.apollographql.apollo.ApolloClient
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient

internal object AndroidGraphQlClient {
    /**
     * @param serverUrl `https://example.com` の形
     */
    fun create(serverUrl: String): ApolloClient = GraphQlClient.builder("$serverUrl/graphql").build()
}
