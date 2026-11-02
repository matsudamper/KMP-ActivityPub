package net.matsudamper.kmp.activitypub.frontend.logic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Query
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.watch

/**
 * 1 ページ目の [firstQuery] のキャッシュに続きのページを連結していく。画面は [watch] で連結済みの結果を受け取る
 *
 * @param queryAfter [firstQuery] の cursor だけを差し替えたクエリを返す
 * @param nextCursorOf 続きが無ければ null
 * @param appendPage 連結できなければ null
 */
class CursorPagingModel<D : Query.Data> internal constructor(
    private val clients: GraphQlClientSource,
    private val firstQuery: Query<D>,
    private val queryAfter: (cursor: String) -> Query<D>,
    private val nextCursorOf: (D) -> String?,
    private val appendPage: (before: D, page: D) -> D?,
) {
    /**
     * キャッシュがまだ無い間は何も流さない
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun watch(): Flow<D> {
        return clients.client
            .flatMapLatest { client ->
                client?.query(firstQuery)
                    ?.fetchPolicy(FetchPolicy.CacheOnly)
                    ?.watch()
                    ?: emptyFlow()
            }
            .filter { it.exception == null && it.errors.isNullOrEmpty() }
            .mapNotNull { it.data }
    }

    suspend fun refresh(): UpdateOperationResponseResult<D> {
        val client = clients.awaitLoadedClient() ?: return UpdateOperationResponseResult.Error(GraphQlRequestException(NO_SERVER_MESSAGE))
        return client.updateOperation(firstQuery) {
            fetchPage(client, firstQuery)
        }
    }

    suspend fun fetchNext(): UpdateOperationResponseResult<D> {
        val client = clients.awaitLoadedClient() ?: return UpdateOperationResponseResult.Error(GraphQlRequestException(NO_SERVER_MESSAGE))
        return client.updateOperation(firstQuery) update@{ before ->
            if (before == null) return@update fetchPage(client, firstQuery)
            val cursor = nextCursorOf(before) ?: return@update noHasMore()

            val pageResult = fetchPage(client, queryAfter(cursor))
            if (pageResult !is UpdateOperationResponseResult.Success<D>) return@update pageResult
            val page = pageResult.result
            // 取得を待つ間に updateCache で書き換わったフォロー状態を巻き戻さないよう、結合の直前に読み直す
            val latest = client.readCacheOrNull() ?: before
            val merged = appendPage(latest, page.data ?: return@update error(GraphQlRequestException(page.failureMessage())))
                ?: return@update error(GraphQlRequestException(NOT_LOGGED_IN_MESSAGE))

            success(page.newBuilder().data(merged).build())
        }
    }

    /**
     * 取得し直さずにキャッシュの内容を書き換える。キャッシュが無ければ何もしない
     */
    suspend fun updateCache(transform: (D) -> D) {
        val client = clients.client.value ?: return
        val data = client.readCacheOrNull() ?: return
        client.apolloStore.writeOperation(operation = firstQuery, data = transform(data), publish = true)
    }

    private suspend fun ApolloClient.readCacheOrNull(): D? {
        val response = apolloStore.readOperation(firstQuery)
        if (!response.errors.isNullOrEmpty()) return null
        return response.data
    }

    private suspend fun UpdateOperationResponseScope<D>.fetchPage(
        client: ApolloClient,
        query: Query<D>,
    ): UpdateOperationResponseResult<D> {
        val response = client.query(query)
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .execute()
        if (response.exception != null || response.hasErrors() || response.data == null) {
            return error(GraphQlRequestException(response.failureMessage()))
        }
        return success(response)
    }

    private companion object {
        const val NO_SERVER_MESSAGE = "接続先が未設定"
        const val NOT_LOGGED_IN_MESSAGE = "ログインしていません"
    }
}

class GraphQlRequestException(
    message: String,
) : Exception(message)

internal fun ApolloResponse<*>.failureMessage(): String {
    return exception?.message
        ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
        ?: "ネットワークエラー"
}
