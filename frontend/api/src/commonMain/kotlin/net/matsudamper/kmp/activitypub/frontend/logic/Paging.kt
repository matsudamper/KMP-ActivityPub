package net.matsudamper.kmp.activitypub.frontend.logic

import kotlinx.coroutines.flow.Flow

interface Paging<T> {
    fun watch(): Flow<T>

    /**
     * 続きを取って一覧に足す。一覧は watch から流れるので、返すのは失敗したかどうかだけ
     */
    suspend fun loadMore(cursor: String): PagingLoadMoreResult
}

sealed interface PagingLoadMoreResult {
    data object Success : PagingLoadMoreResult

    data class Failure(
        val message: String,
    ) : PagingLoadMoreResult
}
