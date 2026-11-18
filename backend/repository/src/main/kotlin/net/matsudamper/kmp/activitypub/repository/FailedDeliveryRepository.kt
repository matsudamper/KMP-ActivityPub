package net.matsudamper.kmp.activitypub.repository

import java.time.Instant

/**
 * 諦めた配信を管理画面で見る。諦めた行は [DeliveryQueueRepository.giveUp] で failed にしたもの
 */
interface FailedDeliveryRepository {
    /**
     * 新しく積んだ順
     *
     * @param beforeId この id より前に積んだ分を返す。最初のページなら null
     */
    fun page(
        beforeId: Long?,
        limit: Int,
    ): List<FailedDelivery>

    /**
     * @return 諦めた配信でなければ null
     */
    fun find(id: Long): FailedDelivery?
}

/**
 * @param username 署名したこちらのアカウント
 * @param attempts 取り出された回数
 * @param failedAt 諦めた時刻を記録する前に諦めた行では null
 */
data class FailedDelivery(
    val id: Long,
    val kind: String,
    val username: String,
    val inbox: String,
    val attempts: Int,
    val enqueuedAt: Instant,
    val failedAt: Instant?,
    val lastError: String?,
)
