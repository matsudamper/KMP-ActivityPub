package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.PendingFollowAccept
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.FOLLOWERS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.FOLLOW_ACCEPT_DELIVERIES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

internal class SqliteFollowerRepository(
    private val jooq: SqliteJooq,
) : FollowerRepository {
    override fun record(
        username: String,
        follower: RemoteActorRecord,
        followActivityUri: String,
        receivedAt: Instant,
        acceptBody: String,
        acceptNextAttemptAt: Instant,
    ): Boolean = jooq.transaction { dsl ->
        val accountId = dsl.findAccountId(username) ?: return@transaction false

        val profile = follower.profile
        dsl.insertInto(REMOTE_ACTORS)
            .set(REMOTE_ACTORS.ACTOR_URI, follower.actorUri)
            .set(REMOTE_ACTORS.INBOX, follower.inbox)
            .set(REMOTE_ACTORS.SHARED_INBOX, follower.sharedInbox)
            .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, follower.publicKeyPem)
            .set(REMOTE_ACTORS.PREFERRED_USERNAME, profile.preferredUsername)
            .set(REMOTE_ACTORS.DISPLAY_NAME, profile.displayName)
            .set(REMOTE_ACTORS.PROFILE_URL, profile.profileUrl)
            .set(REMOTE_ACTORS.ICON_URL, profile.iconUrl)
            .onConflict(REMOTE_ACTORS.ACTOR_URI)
            .doUpdate()
            .set(REMOTE_ACTORS.INBOX, follower.inbox)
            .set(REMOTE_ACTORS.SHARED_INBOX, follower.sharedInbox)
            .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, follower.publicKeyPem)
            .set(REMOTE_ACTORS.PREFERRED_USERNAME, profile.preferredUsername)
            .set(REMOTE_ACTORS.DISPLAY_NAME, profile.displayName)
            .set(REMOTE_ACTORS.PROFILE_URL, profile.profileUrl)
            .set(REMOTE_ACTORS.ICON_URL, profile.iconUrl)
            .execute()
        val remoteActorId =
            checkNotNull(dsl.findRemoteActorId(follower.actorUri)) { "記録したアクターを引けない: ${follower.actorUri}" }

        dsl.insertInto(FOLLOWERS)
            .set(FOLLOWERS.ACCOUNT_ID, accountId)
            .set(FOLLOWERS.REMOTE_ACTOR_ID, remoteActorId)
            .set(FOLLOWERS.FOLLOW_ACTIVITY_URI, followActivityUri)
            .set(FOLLOWERS.RECEIVED_AT, StoredInstant.format(receivedAt))
            .onConflict(FOLLOWERS.ACCOUNT_ID, FOLLOWERS.REMOTE_ACTOR_ID)
            .doUpdate()
            .set(FOLLOWERS.FOLLOW_ACTIVITY_URI, followActivityUri)
            .set(FOLLOWERS.RECEIVED_AT, StoredInstant.format(receivedAt))
            .execute()

        dsl.insertInto(FOLLOW_ACCEPT_DELIVERIES)
            .set(FOLLOW_ACCEPT_DELIVERIES.ACCOUNT_ID, accountId)
            .set(FOLLOW_ACCEPT_DELIVERIES.REMOTE_ACTOR_ID, remoteActorId)
            .set(FOLLOW_ACCEPT_DELIVERIES.BODY, acceptBody)
            .set(FOLLOW_ACCEPT_DELIVERIES.ATTEMPTS, 0L)
            .set(FOLLOW_ACCEPT_DELIVERIES.NEXT_ATTEMPT_AT, StoredInstant.format(acceptNextAttemptAt))
            .onConflict(FOLLOW_ACCEPT_DELIVERIES.ACCOUNT_ID, FOLLOW_ACCEPT_DELIVERIES.REMOTE_ACTOR_ID)
            .doUpdate()
            .set(FOLLOW_ACCEPT_DELIVERIES.BODY, acceptBody)
            .set(FOLLOW_ACCEPT_DELIVERIES.ATTEMPTS, 0L)
            .set(FOLLOW_ACCEPT_DELIVERIES.NEXT_ATTEMPT_AT, StoredInstant.format(acceptNextAttemptAt))
            .execute()
        true
    }

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = jooq.withConnection { dsl ->
        val activityCondition = followActivityUri?.let { FOLLOWERS.FOLLOW_ACTIVITY_URI.eq(it) } ?: DSL.noCondition()
        dsl.deleteFrom(FOLLOWERS)
            .where(FOLLOWERS.ACCOUNT_ID.eq(accountIdOf(username)))
            .and(FOLLOWERS.REMOTE_ACTOR_ID.eq(remoteActorIdOf(followerActorUri)))
            .and(activityCondition)
            .execute() > 0
    }

    override fun findFolloweeUsername(
        followerActorUri: String,
        followActivityUri: String,
    ): String? = jooq.withConnection { dsl ->
        dsl.select(ACCOUNTS.USERNAME)
            .from(FOLLOWERS)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(FOLLOWERS.ACCOUNT_ID))
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(FOLLOWERS.REMOTE_ACTOR_ID))
            .where(REMOTE_ACTORS.ACTOR_URI.eq(followerActorUri))
            .and(FOLLOWERS.FOLLOW_ACTIVITY_URI.eq(followActivityUri))
            .fetchOne(ACCOUNTS.USERNAME)
    }

    override fun removeAccount(username: String): Int = jooq.withConnection { dsl ->
        dsl.deleteFrom(FOLLOWERS)
            .where(FOLLOWERS.ACCOUNT_ID.eq(accountIdOf(username)))
            .execute()
    }

    override fun removeRemoteActor(actorUri: String): Int = jooq.transaction { dsl ->
        val remoteActorId = dsl.findRemoteActorId(actorUri) ?: return@transaction 0
        val removedFollows = dsl.deleteFrom(FOLLOWERS)
            .where(FOLLOWERS.REMOTE_ACTOR_ID.eq(remoteActorId))
            .execute()
        dsl.deleteFrom(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.ID.eq(remoteActorId))
            .execute()
        removedFollows
    }

    override fun findPublicKeyPem(actorUri: String): String? = jooq.withConnection { dsl ->
        dsl.select(REMOTE_ACTORS.PUBLIC_KEY_PEM)
            .from(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .fetchOne(REMOTE_ACTORS.PUBLIC_KEY_PEM)
    }

    override fun updatePublicKeyPem(
        actorUri: String,
        publicKeyPem: String,
    ) {
        jooq.withConnection { dsl ->
            dsl.update(REMOTE_ACTORS)
                .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, publicKeyPem)
                .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
                .execute()
        }
    }

    override fun updateProfile(
        actorUri: String,
        profile: RemoteActorProfileRecord,
    ) {
        jooq.withConnection { dsl ->
            dsl.update(REMOTE_ACTORS)
                .set(REMOTE_ACTORS.PREFERRED_USERNAME, profile.preferredUsername)
                .set(REMOTE_ACTORS.DISPLAY_NAME, profile.displayName)
                .set(REMOTE_ACTORS.PROFILE_URL, profile.profileUrl)
                .set(REMOTE_ACTORS.ICON_URL, profile.iconUrl)
                .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
                .execute()
        }
    }

    override fun listAccepted(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = jooq.withConnection { dsl ->
        val afterCondition = after?.let { REMOTE_ACTORS.ACTOR_URI.gt(it) } ?: DSL.noCondition()
        dsl.select(REMOTE_ACTORS.ACTOR_URI)
            .from(FOLLOWERS)
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(FOLLOWERS.REMOTE_ACTOR_ID))
            .where(acceptedFollowersOf(username))
            .and(afterCondition)
            .orderBy(REMOTE_ACTORS.ACTOR_URI)
            .limit(limit)
            .fetch(REMOTE_ACTORS.ACTOR_URI)
            .filterNotNull()
    }

    override fun countAccepted(username: String): Long = jooq.withConnection { dsl ->
        dsl.fetchCount(
            dsl.selectFrom(FOLLOWERS).where(acceptedFollowersOf(username)),
        ).toLong()
    }

    override fun deliveryTargets(username: String): List<String> = jooq.withConnection { dsl ->
        val target = DSL.coalesce(REMOTE_ACTORS.SHARED_INBOX, REMOTE_ACTORS.INBOX)
        dsl.selectDistinct(target)
            .from(FOLLOWERS)
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(FOLLOWERS.REMOTE_ACTOR_ID))
            .where(acceptedFollowersOf(username))
            .orderBy(target)
            .fetch(target)
            .filterNotNull()
    }

    override fun findDueAccepts(
        now: Instant,
        limit: Int,
    ): List<PendingFollowAccept> = jooq.withConnection { dsl ->
        dsl.select(
            ACCOUNTS.USERNAME,
            REMOTE_ACTORS.ACTOR_URI,
            REMOTE_ACTORS.INBOX,
            FOLLOW_ACCEPT_DELIVERIES.BODY,
            FOLLOW_ACCEPT_DELIVERIES.ATTEMPTS,
        )
            .from(FOLLOW_ACCEPT_DELIVERIES)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(FOLLOW_ACCEPT_DELIVERIES.ACCOUNT_ID))
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(FOLLOW_ACCEPT_DELIVERIES.REMOTE_ACTOR_ID))
            .where(FOLLOW_ACCEPT_DELIVERIES.NEXT_ATTEMPT_AT.le(StoredInstant.format(now)))
            .orderBy(FOLLOW_ACCEPT_DELIVERIES.NEXT_ATTEMPT_AT)
            .limit(limit)
            .fetch()
            .map { record ->
                PendingFollowAccept(
                    followeeUsername = checkNotNull(record.get(ACCOUNTS.USERNAME)),
                    followerActorUri = checkNotNull(record.get(REMOTE_ACTORS.ACTOR_URI)),
                    followerInbox = checkNotNull(record.get(REMOTE_ACTORS.INBOX)),
                    body = checkNotNull(record.get(FOLLOW_ACCEPT_DELIVERIES.BODY)),
                    attempts = checkNotNull(record.get(FOLLOW_ACCEPT_DELIVERIES.ATTEMPTS)).toInt(),
                )
            }
    }

    override fun markAccepted(
        accept: PendingFollowAccept,
        acceptedAt: Instant,
    ) {
        jooq.transaction { dsl ->
            val deleted = dsl.deleteFrom(FOLLOW_ACCEPT_DELIVERIES)
                .where(deliveryOf(accept))
                .execute()
            // 送っている間に新しい Follow で置き換えられていたら、そちらの Accept は送れていない
            if (deleted == 0) return@transaction

            dsl.update(FOLLOWERS)
                .set(FOLLOWERS.ACCEPTED_AT, DSL.coalesce(FOLLOWERS.ACCEPTED_AT, DSL.inline(StoredInstant.format(acceptedAt))))
                .where(FOLLOWERS.ACCOUNT_ID.eq(accountIdOf(accept.followeeUsername)))
                .and(FOLLOWERS.REMOTE_ACTOR_ID.eq(remoteActorIdOf(accept.followerActorUri)))
                .execute()
        }
    }

    override fun postponeAccept(
        accept: PendingFollowAccept,
        nextAttemptAt: Instant,
    ) {
        jooq.withConnection { dsl ->
            dsl.update(FOLLOW_ACCEPT_DELIVERIES)
                .set(FOLLOW_ACCEPT_DELIVERIES.ATTEMPTS, accept.attempts + 1L)
                .set(FOLLOW_ACCEPT_DELIVERIES.NEXT_ATTEMPT_AT, StoredInstant.format(nextAttemptAt))
                .where(deliveryOf(accept))
                .execute()
        }
    }

    override fun giveUpAccept(accept: PendingFollowAccept) {
        jooq.transaction { dsl ->
            val deleted = dsl.deleteFrom(FOLLOW_ACCEPT_DELIVERIES)
                .where(deliveryOf(accept))
                .execute()
            if (deleted == 0) return@transaction

            // 一度成立したフォローは、送り直しの Accept を諦めても成立したまま
            dsl.deleteFrom(FOLLOWERS)
                .where(FOLLOWERS.ACCOUNT_ID.eq(accountIdOf(accept.followeeUsername)))
                .and(FOLLOWERS.REMOTE_ACTOR_ID.eq(remoteActorIdOf(accept.followerActorUri)))
                .and(FOLLOWERS.ACCEPTED_AT.isNull)
                .execute()
        }
    }

    private fun deliveryOf(accept: PendingFollowAccept): Condition =
        FOLLOW_ACCEPT_DELIVERIES.ACCOUNT_ID.eq(accountIdOf(accept.followeeUsername))
            .and(FOLLOW_ACCEPT_DELIVERIES.REMOTE_ACTOR_ID.eq(remoteActorIdOf(accept.followerActorUri)))
            .and(FOLLOW_ACCEPT_DELIVERIES.BODY.eq(accept.body))

    private fun acceptedFollowersOf(username: String): Condition =
        FOLLOWERS.ACCOUNT_ID.eq(accountIdOf(username))
            .and(FOLLOWERS.ACCEPTED_AT.isNotNull)

    private fun accountIdOf(username: String) =
        DSL.select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.USERNAME.eq(username.lowercase()))

    private fun remoteActorIdOf(actorUri: String) =
        DSL.select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))

    private fun DSLContext.findAccountId(username: String): Long? =
        select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.USERNAME.eq(username.lowercase())).fetchOne(ACCOUNTS.ID)

    private fun DSLContext.findRemoteActorId(actorUri: String): Long? =
        select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).fetchOne(REMOTE_ACTORS.ID)
}
