package net.matsudamper.kmp.activitypub.frontend.logic

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import net.matsudamper.kmp.activitypub.frontend.graphql.cache.cache

object GraphQlClient {
    fun builder(serverUrl: String): ApolloClient.Builder =
        ApolloClient.Builder()
            .serverUrl(serverUrl)
            .cache(MemoryCacheFactory(maxSizeBytes = 10 * 1024 * 1024))
            .fetchPolicy(FetchPolicy.NetworkOnly)
}
