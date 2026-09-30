package net.matsudamper.kmp.activitypub.frontend.logic

import com.apollographql.apollo.ApolloClient
import com.apollographql.cache.normalized.memory.MemoryCacheFactory
import net.matsudamper.kmp.activitypub.frontend.graphql.cache.Cache.cache
import net.matsudamper.kmp.activitypub.shared.GRAPHQL_PATH

object GraphQlClient {
    private const val CACHE_SIZE_BYTES = 10 * 1024 * 1024

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
            .cache(MemoryCacheFactory(maxSizeBytes = CACHE_SIZE_BYTES))
}
