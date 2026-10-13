package net.matsudamper.kmp.activitypub.frontend.logic

import com.apollographql.apollo.ApolloClient
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH

object GraphQlClient {
    val apollo: ApolloClient by lazy { builder(GRAPHQL_PATH).build() }

    fun builder(serverUrl: String): ApolloClient.Builder =
        ApolloClient
            .Builder()
            .serverUrl(serverUrl)
}
