package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.ActorBlockRepository
import net.matsudamper.kmp.activitypub.repository.BlockedActor
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_ACTOR_BLOCKS
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

    override fun remove(accountId: AccountId, actorUri: String) {
        jooq.withConnection { dsl ->
            dsl.deleteFrom(ACCOUNT_ACTOR_BLOCKS)
                .where(ACCOUNT_ACTOR_BLOCKS.ACCOUNT_ID.eq(accountId.value))
                .and(ACCOUNT_ACTOR_BLOCKS.ACTOR_URI.eq(actorUri))
                .execute()
        }
    }
}
