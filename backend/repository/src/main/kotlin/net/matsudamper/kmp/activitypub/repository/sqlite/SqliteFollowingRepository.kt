package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_FOLLOWING
import net.matsudamper.kmp.activitypub.shared.AccountId

internal class SqliteFollowingRepository(
    private val jooq: SqliteJooq,
) : FollowingRepository {
    override fun contains(accountId: AccountId, actorUri: String): Boolean = jooq.withConnection { dsl ->
        dsl.fetchExists(ACCOUNT_FOLLOWING, ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value).and(ACCOUNT_FOLLOWING.ACTOR_URI.eq(actorUri)))
    }

    override fun add(accountId: AccountId, actorUri: String, createdAt: Instant): Boolean = jooq.withConnection { dsl ->
        dsl.insertInto(ACCOUNT_FOLLOWING)
            .set(ACCOUNT_FOLLOWING.ACCOUNT_ID, accountId.value)
            .set(ACCOUNT_FOLLOWING.ACTOR_URI, actorUri)
            .set(ACCOUNT_FOLLOWING.CREATED_AT, StoredInstant.format(createdAt))
            .onConflict(ACCOUNT_FOLLOWING.ACCOUNT_ID, ACCOUNT_FOLLOWING.ACTOR_URI).doNothing()
            .execute() > 0
    }

    override fun remove(accountId: AccountId, actorUri: String): Boolean = jooq.withConnection { dsl ->
        dsl.deleteFrom(ACCOUNT_FOLLOWING)
            .where(ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value))
            .and(ACCOUNT_FOLLOWING.ACTOR_URI.eq(actorUri))
            .execute() > 0
    }
}
