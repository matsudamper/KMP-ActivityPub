package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.UnknownObjectType
import net.matsudamper.kmp.activitypub.repository.UnknownObjectTypeRepository

/**
 * 届いたものの中にあった、読み方を知らない ActivityPub の type を管理画面で見る
 */
class UnknownObjectTypeService(
    private val repository: UnknownObjectTypeRepository,
) {
    /**
     * 初めて見た新しい順
     *
     * @param after 前回の [UnknownObjectTypePage.nextCursor]。最初のページなら null
     * @return [after] が読めなければ null
     */
    fun page(after: String?, limit: Int): UnknownObjectTypePage? {
        val beforeId = if (after == null) null else after.toLongOrNull() ?: return null
        val pageSize = limit.coerceIn(1, MAX_PAGE_SIZE)
        val types = repository.page(beforeId = beforeId, limit = pageSize)
        return UnknownObjectTypePage(
            types = types,
            nextCursor = types.lastOrNull()?.takeIf { types.size == pageSize }?.id?.toString(),
        )
    }

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

/**
 * @param nextCursor 続きが無ければ null
 */
data class UnknownObjectTypePage(
    val types: List<UnknownObjectType>,
    val nextCursor: String?,
)
