package net.matsudamper.kmp.activitypub.frontend.logic

import kotlin.coroutines.cancellation.CancellationException
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.cache.normalized.apolloStore

sealed interface UpdateOperationResponseResult<D : Operation.Data> {
    data class Success<D : Operation.Data>(val result: ApolloResponse<D>) : UpdateOperationResponseResult<D>
    data class Error<D : Operation.Data>(val e: Throwable?) : UpdateOperationResponseResult<D>
    class NoHasMore<D : Operation.Data> : UpdateOperationResponseResult<D>

    fun getError(): Error<D>? = this as? Error<D>
    fun getOrNull(): D? = (this as? Success<D>)?.result?.data
}

class UpdateOperationResponseScope<D : Operation.Data> {
    fun success(result: ApolloResponse<D>): UpdateOperationResponseResult.Success<D> = UpdateOperationResponseResult.Success(result)

    fun error(e: Throwable? = null): UpdateOperationResponseResult.Error<D> = UpdateOperationResponseResult.Error(e)

    fun noHasMore(): UpdateOperationResponseResult.NoHasMore<D> = UpdateOperationResponseResult.NoHasMore()
}

/**
 * [cacheQueryKey] のキャッシュを [block] の結果で置き換える。画面は [cacheQueryKey] を watch して結果を受け取る
 */
suspend fun <D : Operation.Data> ApolloClient.updateOperation(
    cacheQueryKey: Operation<D>,
    block: suspend UpdateOperationResponseScope<D>.(D?) -> UpdateOperationResponseResult<D>,
): UpdateOperationResponseResult<D> {
    val before = readOperationOrNull(cacheQueryKey)
    val result =
        try {
            block(UpdateOperationResponseScope(), before)
        } catch (e: CancellationException) {
            // キャンセルを失敗として扱うと、取り消した読み込みの結果で画面を書き換えてしまう
            throw e
        } catch (e: Throwable) {
            return UpdateOperationResponseResult.Error(e)
        }
    if (result is UpdateOperationResponseResult.Success<D>) {
        val data = result.result.data ?: return UpdateOperationResponseResult.Error(NullPointerException("ApolloResponse.data is null"))
        apolloStore.writeOperation(
            operation = cacheQueryKey,
            data = data,
            publish = true,
        )
    }
    return result
}

private suspend fun <D : Operation.Data> ApolloClient.readOperationOrNull(query: Operation<D>): D? {
    // 欠けたフィールドは errors に入った部分データとして返るため、欠けがあれば未キャッシュとして扱う
    val response = apolloStore.readOperation(query)
    if (!response.errors.isNullOrEmpty()) return null
    return response.data
}
