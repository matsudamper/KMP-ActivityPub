package net.matsudamper.kmp.activitypub.frontend.logic

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import net.matsudamper.kmp.activitypub.frontend.graphql.cache.Cache.cache

object GraphQlClient {
    private const val CACHE_MAX_SIZE_BYTES = 10 * 1024 * 1024

    fun builder(serverUrl: String): ApolloClient.Builder =
        ApolloClient.Builder()
            .serverUrl(serverUrl)
            .cache(MemoryCacheFactory(maxSizeBytes = CACHE_MAX_SIZE_BYTES))
}
