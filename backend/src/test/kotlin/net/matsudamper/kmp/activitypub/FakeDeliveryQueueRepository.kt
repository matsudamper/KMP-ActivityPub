package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.ClaimedDelivery
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository

/**
 * 何も積まれていない状態だけを返す
 */
internal class FakeDeliveryQueueRepository : DeliveryQueueRepository {
    override fun claim(
        now: Instant,
        limit: Int,
    ): List<ClaimedDelivery> = listOf()

    override fun markDelivered(
        id: Long,
        deliveredAt: Instant,
    ) = Unit

    override fun scheduleRetry(
        id: Long,
        nextAttemptAt: Instant,
        error: String,
    ) = Unit

    override fun giveUp(
        id: Long,
        error: String,
    ) = Unit

    override fun recoverDelivering(now: Instant): Int = 0
}
