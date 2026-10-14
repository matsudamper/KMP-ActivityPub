package net.matsudamper.kmp.activitypub.frontend.logic

import com.apollographql.apollo.ApolloClient

object GraphQlClient {
    val apollo: ApolloClient by lazy { builder("/graphql").build() }

    fun builder(serverUrl: String): ApolloClient.Builder = ApolloClient.Builder().serverUrl(serverUrl)
}
