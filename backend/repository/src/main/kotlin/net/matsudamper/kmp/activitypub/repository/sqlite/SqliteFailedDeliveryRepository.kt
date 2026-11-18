package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.FailedDelivery
import net.matsudamper.kmp.activitypub.repository.FailedDeliveryRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.DELIVERY_QUEUE
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

internal class SqliteFailedDeliveryRepository(
    private val jooq: SqliteJooq,
) : FailedDeliveryRepository {
    override fun page(
        beforeId: Long?,
        limit: Int,
    ): List<FailedDelivery> = jooq.withConnection { dsl ->
        dsl.selectFailed(if (beforeId == null) DSL.noCondition() else DELIVERY_QUEUE.ID.lt(beforeId))
            .orderBy(DELIVERY_QUEUE.ID.desc())
            .limit(limit)
            .fetch { it.toFailedDelivery() }
    }

    override fun find(id: Long): FailedDelivery? = jooq.withConnection { dsl ->
        dsl.selectFailed(DELIVERY_QUEUE.ID.eq(id)).fetchOne { it.toFailedDelivery() }
    }

    private fun DSLContext.selectFailed(condition: Condition) =
        select(
            DELIVERY_QUEUE.ID,
            DELIVERY_QUEUE.KIND,
            ACCOUNTS.USERNAME,
            DELIVERY_QUEUE.INBOX,
            DELIVERY_QUEUE.ATTEMPTS,
            DELIVERY_QUEUE.ENQUEUED_AT,
            DELIVERY_QUEUE.FAILED_AT,
            DELIVERY_QUEUE.LAST_ERROR,
        )
            .from(DELIVERY_QUEUE)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(DELIVERY_QUEUE.ACCOUNT_ID))
            .where(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_FAILED))
            .and(condition)

    private fun Record.toFailedDelivery(): FailedDelivery = FailedDelivery(
        id = checkNotNull(get(DELIVERY_QUEUE.ID)),
        kind = checkNotNull(get(DELIVERY_QUEUE.KIND)),
        username = checkNotNull(get(ACCOUNTS.USERNAME)),
        inbox = checkNotNull(get(DELIVERY_QUEUE.INBOX)),
        attempts = checkNotNull(get(DELIVERY_QUEUE.ATTEMPTS)).toInt(),
        enqueuedAt = StoredInstant.parse(checkNotNull(get(DELIVERY_QUEUE.ENQUEUED_AT))),
        failedAt = get(DELIVERY_QUEUE.FAILED_AT)?.let(StoredInstant::parse),
        lastError = get(DELIVERY_QUEUE.LAST_ERROR),
    )
}
