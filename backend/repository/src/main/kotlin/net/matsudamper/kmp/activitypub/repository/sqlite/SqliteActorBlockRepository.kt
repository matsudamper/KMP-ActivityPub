package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.ActorBlockRepository
import net.matsudamper.kmp.activitypub.repository.BlockedActor
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_ACTOR_BLOCKS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.DELIVERY_QUEUE
import net.matsudamper.kmp.activitypub.shared.AccountId

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

    override fun remove(accountId: AccountId, actorUri: String): String? = jooq.withConnection { dsl ->
        dsl.deleteFrom(ACCOUNT_ACTOR_BLOCKS)
            .where(ACCOUNT_ACTOR_BLOCKS.ACCOUNT_ID.eq(accountId.value))
            .and(ACCOUNT_ACTOR_BLOCKS.ACTOR_URI.eq(actorUri))
            .returning(ACCOUNT_ACTOR_BLOCKS.BLOCK_ACTIVITY_ID)
            .fetchOne(ACCOUNT_ACTOR_BLOCKS.BLOCK_ACTIVITY_ID)
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
            .where(ACCOUNT_ACTOR_BLOCKS.ACCOUNT_ID.eq(accountId.value))
            .and(ACCOUNT_ACTOR_BLOCKS.ACTOR_URI.eq(actorUri))
            .and(ACCOUNT_ACTOR_BLOCKS.BLOCK_ACTIVITY_ID.isNull)
            .execute()
        if (updated == 0) return@transaction false
        dsl.insertInto(DELIVERY_QUEUE)
            .set(DELIVERY_QUEUE.KIND, DeliveryQueueValues.KIND_BLOCK)
            .set(DELIVERY_QUEUE.ACCOUNT_ID, accountId.value)
            .set(DELIVERY_QUEUE.INBOX, inbox)
            .set(DELIVERY_QUEUE.BODY, body)
            .set(DELIVERY_QUEUE.STATE, DeliveryQueueValues.STATE_PENDING)
            .set(DELIVERY_QUEUE.ATTEMPTS, 0L)
            .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, StoredInstant.format(now))
            .set(DELIVERY_QUEUE.ENQUEUED_AT, StoredInstant.format(now))
            .execute()
        true
    }
}
