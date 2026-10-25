package net.matsudamper.kmp.activitypub.frontend.logic

import com.apollographql.apollo.ApolloClient

object GraphQlClient {
    fun builder(serverUrl: String): ApolloClient.Builder = ApolloClient.Builder().serverUrl(serverUrl)
}
