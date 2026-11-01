package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.ActorListPosition
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.ListedActor
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_FOLLOWING
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.jooq.Condition
import org.jooq.impl.DSL

internal class SqliteFollowingRepository(
    private val jooq: SqliteJooq,
) : FollowingRepository {
    override fun followId(accountId: AccountId, actorUri: String): String? = jooq.withConnection { dsl ->
        dsl.select(ACCOUNT_FOLLOWING.FOLLOW_ACTIVITY_URI).from(ACCOUNT_FOLLOWING)
            .where(ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value)).and(ACCOUNT_FOLLOWING.ACTOR_URI.eq(actorUri))
            .fetchOne(ACCOUNT_FOLLOWING.FOLLOW_ACTIVITY_URI)
    }

    override fun recordActivity(accountId: AccountId, actorUri: String, followId: String) {
        jooq.withConnection { dsl ->
            dsl.update(ACCOUNT_FOLLOWING).set(ACCOUNT_FOLLOWING.FOLLOW_ACTIVITY_URI, followId).setNull(ACCOUNT_FOLLOWING.ACCEPTED_AT)
                .where(ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value)).and(ACCOUNT_FOLLOWING.ACTOR_URI.eq(actorUri)).execute()
        }
    }

    override fun respondToFollow(actorUri: String, followId: String, acceptedAt: Instant?) {
        jooq.withConnection { dsl ->
            val matches = ACCOUNT_FOLLOWING.ACTOR_URI.eq(actorUri).and(ACCOUNT_FOLLOWING.FOLLOW_ACTIVITY_URI.eq(followId))
            if (acceptedAt == null) {
                dsl.deleteFrom(ACCOUNT_FOLLOWING).where(matches).execute()
            } else {
                dsl.update(ACCOUNT_FOLLOWING).set(ACCOUNT_FOLLOWING.ACCEPTED_AT, StoredInstant.format(acceptedAt))
                    .where(matches).and(ACCOUNT_FOLLOWING.ACCEPTED_AT.isNull).execute()
            }
        }
    }

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

    override fun list(accountId: AccountId, after: ActorListPosition?, limit: Int): List<ListedActor> = jooq.withConnection { dsl ->
        dsl
            .select(
                ACCOUNT_FOLLOWING.ACTOR_URI,
                ACCOUNT_FOLLOWING.CREATED_AT,
                REMOTE_ACTORS.PREFERRED_USERNAME,
                REMOTE_ACTORS.DISPLAY_NAME,
                REMOTE_ACTORS.ICON_URL,
                REMOTE_ACTORS.UPDATED_AT,
            )
            .from(ACCOUNT_FOLLOWING)
            .leftJoin(REMOTE_ACTORS).on(REMOTE_ACTORS.ACTOR_URI.eq(ACCOUNT_FOLLOWING.ACTOR_URI))
            .where(ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value))
            .and(olderThan(after))
            .orderBy(ACCOUNT_FOLLOWING.CREATED_AT.desc(), ACCOUNT_FOLLOWING.ACTOR_URI.desc())
            .limit(limit)
            .fetch { record ->
                ListedActor(
                    actorUri = checkNotNull(record.get(ACCOUNT_FOLLOWING.ACTOR_URI)),
                    listedAt = StoredInstant.parse(checkNotNull(record.get(ACCOUNT_FOLLOWING.CREATED_AT))),
                    preferredUsername = record.get(REMOTE_ACTORS.PREFERRED_USERNAME),
                    displayName = record.get(REMOTE_ACTORS.DISPLAY_NAME),
                    iconUrl = record.get(REMOTE_ACTORS.ICON_URL),
                    actorUpdatedAt = record.get(REMOTE_ACTORS.UPDATED_AT)?.let { StoredInstant.parse(it) },
                )
            }
    }

    override fun count(accountId: AccountId): Long = jooq.withConnection { dsl ->
        dsl.fetchCount(ACCOUNT_FOLLOWING, ACCOUNT_FOLLOWING.ACCOUNT_ID.eq(accountId.value)).toLong()
    }

    private fun olderThan(position: ActorListPosition?): Condition {
        if (position == null) return DSL.noCondition()
        val createdAt = StoredInstant.format(position.listedAt)
        return ACCOUNT_FOLLOWING.CREATED_AT.lt(createdAt)
            .or(ACCOUNT_FOLLOWING.CREATED_AT.eq(createdAt).and(ACCOUNT_FOLLOWING.ACTOR_URI.lt(position.actorUri)))
    }
}
