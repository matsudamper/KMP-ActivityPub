package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.FailedDelivery
import net.matsudamper.kmp.activitypub.repository.FailedDeliveryRepository
import net.matsudamper.kmp.activitypub.shared.FailedDeliveryId

/**
 * 諦めた配信を管理画面で見る
 */
class FailedDeliveryService(
    private val repository: FailedDeliveryRepository,
) {
    /**
     * 新しく積んだ順
     *
     * @param after 前回の [FailedDeliveryPage.nextCursor]。最初のページなら null
     * @return [after] が読めなければ null
     */
    fun page(after: String?, limit: Int): FailedDeliveryPage? {
        val beforeId = if (after == null) null else after.toLongOrNull() ?: return null
        val pageSize = limit.coerceIn(1, MAX_PAGE_SIZE)
        val deliveries = repository.page(beforeId = beforeId, limit = pageSize)
        return FailedDeliveryPage(
            deliveries = deliveries,
            nextCursor = deliveries.lastOrNull()?.takeIf { deliveries.size == pageSize }?.id?.toString(),
        )
    }

    fun find(id: FailedDeliveryId): FailedDelivery? = repository.find(id.value)

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}

/**
 * @param nextCursor 続きが無ければ null
 */
data class FailedDeliveryPage(
    val deliveries: List<FailedDelivery>,
    val nextCursor: String?,
)
