package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.ActorBlockRepository
import net.matsudamper.kmp.activitypub.repository.BlockedActor
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_ACTOR_BLOCKS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.DELIVERY_QUEUE
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.jooq.impl.DSL

internal class SqliteActorBlockRepository(
    private val jooq: SqliteJooq,
) : ActorBlockRepository {
    override fun list(accountId: AccountId): List<BlockedActor> = jooq.withConnection { dsl ->
        dsl.select(ACCOUNT_ACTOR_BLOCKS.ACTOR_URI, ACCOUNT_ACTOR_BLOCKS.ACCT).from(ACCOUNT_ACTOR_BLOCKS)
            .where(ACCOUNT_ACTOR_BLOCKS.ACCOUNT_ID.eq(accountId.value))
            .orderBy(ACCOUNT_ACTOR_BLOCKS.CREATED_AT.desc(), ACCOUNT_ACTOR_BLOCKS.ACTOR_URI)
            .fetch { record ->
                BlockedActor(
                    actorUri = checkNotNull(record.get(ACCOUNT_ACTOR_BLOCKS.ACTOR_URI)),
                    acct = checkNotNull(record.get(ACCOUNT_ACTOR_BLOCKS.ACCT)),
                )
            }
    }

    override fun add(accountId: AccountId, actor: BlockedActor, createdAt: Instant) {
        jooq.withConnection { dsl ->
            dsl.insertInto(ACCOUNT_ACTOR_BLOCKS)
                .set(ACCOUNT_ACTOR_BLOCKS.ACCOUNT_ID, accountId.value)
                .set(ACCOUNT_ACTOR_BLOCKS.ACTOR_URI, actor.actorUri)
                .set(ACCOUNT_ACTOR_BLOCKS.ACCT, actor.acct)
                .set(ACCOUNT_ACTOR_BLOCKS.CREATED_AT, StoredInstant.format(createdAt))
                .onConflict(ACCOUNT_ACTOR_BLOCKS.ACCOUNT_ID, ACCOUNT_ACTOR_BLOCKS.ACTOR_URI).doUpdate()
                .set(ACCOUNT_ACTOR_BLOCKS.ACCT, actor.acct)
                .execute()
        }
    }

    override fun remove(accountId: AccountId, actorUri: String, now: Instant, undoBody: (blockActivityId: String) -> String?): Boolean = jooq.transaction { dsl ->
        val removed = dsl.deleteFrom(ACCOUNT_ACTOR_BLOCKS)
            .where(ACCOUNT_ACTOR_BLOCKS.ACCOUNT_ID.eq(accountId.value))
            .and(ACCOUNT_ACTOR_BLOCKS.ACTOR_URI.eq(actorUri))
            .returning(ACCOUNT_ACTOR_BLOCKS.BLOCK_ACTIVITY_ID, ACCOUNT_ACTOR_BLOCKS.BLOCK_INBOX)
            .fetchOne() ?: return@transaction false
        val blockActivityId = removed.get(ACCOUNT_ACTOR_BLOCKS.BLOCK_ACTIVITY_ID) ?: return@transaction false
        val blockInbox = removed.get(ACCOUNT_ACTOR_BLOCKS.BLOCK_INBOX) ?: return@transaction false

        // id は送るたびに作り直すので、宛先が変わっても id だけで送った Block が決まる
        val matchingBlock = DELIVERY_QUEUE.ACCOUNT_ID.eq(accountId.value)
            .and(DELIVERY_QUEUE.KIND.eq(DeliveryQueueValues.KIND_BLOCK))
            .and(DSL.field("json_extract({0}, '$.id')", String::class.java, DELIVERY_QUEUE.BODY).eq(blockActivityId))
        val removedAttempts = dsl.deleteFrom(DELIVERY_QUEUE).where(matchingBlock)
            .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_PENDING))
            .returning(DELIVERY_QUEUE.ATTEMPTS).fetch(DELIVERY_QUEUE.ATTEMPTS)
        val cancelledDelivering = dsl.update(DELIVERY_QUEUE)
            .set(DELIVERY_QUEUE.KIND, DeliveryQueueValues.KIND_CANCELLED_BLOCK)
            .where(matchingBlock)
            .and(DELIVERY_QUEUE.STATE.eq(DeliveryQueueValues.STATE_DELIVERING))
            .execute()
        val blockMayHaveArrived = removedAttempts.isEmpty() || removedAttempts.any { it != 0L } || cancelledDelivering > 0
        if (!blockMayHaveArrived) return@transaction false
        val body = undoBody(blockActivityId) ?: return@transaction false
        dsl.enqueueDelivery(accountId, blockInbox, body, StoredInstant.format(now), DeliveryQueueValues.KIND_UNDO_BLOCK)
        true
    }

    override fun findBlockActivityId(accountId: AccountId, actorUri: String): String? = jooq.withConnection { dsl ->
        dsl.select(ACCOUNT_ACTOR_BLOCKS.BLOCK_ACTIVITY_ID).from(ACCOUNT_ACTOR_BLOCKS)
            .where(ACCOUNT_ACTOR_BLOCKS.ACCOUNT_ID.eq(accountId.value))
            .and(ACCOUNT_ACTOR_BLOCKS.ACTOR_URI.eq(actorUri))
            .fetchOne(ACCOUNT_ACTOR_BLOCKS.BLOCK_ACTIVITY_ID)
    }

    override fun recordBlockActivity(
        accountId: AccountId,
        actorUri: String,
        blockActivityId: String,
        inbox: String,
        body: String,
        now: Instant,
    ): Boolean = jooq.transaction { dsl ->
        val updated = dsl.update(ACCOUNT_ACTOR_BLOCKS)
            .set(ACCOUNT_ACTOR_BLOCKS.BLOCK_ACTIVITY_ID, blockActivityId)
            .set(ACCOUNT_ACTOR_BLOCKS.BLOCK_INBOX, inbox)
            .where(ACCOUNT_ACTOR_BLOCKS.ACCOUNT_ID.eq(accountId.value))
            .and(ACCOUNT_ACTOR_BLOCKS.ACTOR_URI.eq(actorUri))
            .and(ACCOUNT_ACTOR_BLOCKS.BLOCK_ACTIVITY_ID.isNull)
            .execute()
        if (updated == 0) return@transaction false
        dsl.enqueueDelivery(accountId, inbox, body, StoredInstant.format(now), DeliveryQueueValues.KIND_BLOCK)
        true
    }
}
