package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNT_FOLLOWERS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.DELIVERY_QUEUE
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
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
    ): Boolean = jooq.transaction { dsl ->
        val accountId = dsl.findAccountId(username) ?: return@transaction false
        val storedReceivedAt = StoredInstant.format(receivedAt)

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
            .set(REMOTE_ACTORS.UPDATED_AT, storedReceivedAt)
            .onConflict(REMOTE_ACTORS.ACTOR_URI)
            .doUpdate()
            .set(REMOTE_ACTORS.INBOX, follower.inbox)
            .set(REMOTE_ACTORS.SHARED_INBOX, follower.sharedInbox)
            .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, follower.publicKeyPem)
            .set(REMOTE_ACTORS.PREFERRED_USERNAME, profile.preferredUsername)
            .set(REMOTE_ACTORS.DISPLAY_NAME, profile.displayName)
            .set(REMOTE_ACTORS.PROFILE_URL, profile.profileUrl)
            .set(REMOTE_ACTORS.ICON_URL, profile.iconUrl)
            .set(REMOTE_ACTORS.UPDATED_AT, storedReceivedAt)
            .execute()
        val remoteActorId =
            checkNotNull(dsl.findRemoteActorId(follower.actorUri)) { "記録したアクターを引けない: ${follower.actorUri}" }

        // 成立済みのフォローに Follow が届き直しても、成立は取り消さない
        dsl.insertInto(ACCOUNT_FOLLOWERS)
            .set(ACCOUNT_FOLLOWERS.ACCOUNT_ID, accountId)
            .set(ACCOUNT_FOLLOWERS.REMOTE_ACTOR_ID, remoteActorId)
            .set(ACCOUNT_FOLLOWERS.FOLLOW_ACTIVITY_URI, followActivityUri)
            .set(ACCOUNT_FOLLOWERS.RECEIVED_AT, storedReceivedAt)
            .onConflict(ACCOUNT_FOLLOWERS.ACCOUNT_ID, ACCOUNT_FOLLOWERS.REMOTE_ACTOR_ID)
            .doUpdate()
            .set(ACCOUNT_FOLLOWERS.FOLLOW_ACTIVITY_URI, followActivityUri)
            .set(ACCOUNT_FOLLOWERS.RECEIVED_AT, storedReceivedAt)
            .execute()

        // 置き換えで id を振り直す。送っている最中の古い Accept の完了で、新しい Follow を成立させないため
        dsl.deleteFrom(DELIVERY_QUEUE)
            .where(DELIVERY_QUEUE.KIND.eq(DeliveryQueueValues.KIND_ACCEPT_FOLLOW))
            .and(DELIVERY_QUEUE.ACCOUNT_ID.eq(accountId))
            .and(DELIVERY_QUEUE.REMOTE_ACTOR_ID.eq(remoteActorId))
            .execute()
        dsl.insertInto(DELIVERY_QUEUE)
            .set(DELIVERY_QUEUE.KIND, DeliveryQueueValues.KIND_ACCEPT_FOLLOW)
            .set(DELIVERY_QUEUE.ACCOUNT_ID, accountId)
            .set(DELIVERY_QUEUE.REMOTE_ACTOR_ID, remoteActorId)
            .set(DELIVERY_QUEUE.INBOX, follower.inbox)
            .set(DELIVERY_QUEUE.BODY, acceptBody)
            .set(DELIVERY_QUEUE.STATE, DeliveryQueueValues.STATE_PENDING)
            .set(DELIVERY_QUEUE.ATTEMPTS, 0L)
            .set(DELIVERY_QUEUE.NEXT_ATTEMPT_AT, storedReceivedAt)
            .set(DELIVERY_QUEUE.ENQUEUED_AT, storedReceivedAt)
            .execute()
        true
    }

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = jooq.withConnection { dsl ->
        val accountId = dsl.findAccountId(username) ?: return@withConnection false
        val remoteActorId = dsl.findRemoteActorId(followerActorUri) ?: return@withConnection false
        val activityCondition =
            if (followActivityUri == null) {
                DSL.noCondition()
            } else {
                ACCOUNT_FOLLOWERS.FOLLOW_ACTIVITY_URI.eq(followActivityUri)
            }
        dsl.deleteFrom(ACCOUNT_FOLLOWERS)
            .where(ACCOUNT_FOLLOWERS.ACCOUNT_ID.eq(accountId))
            .and(ACCOUNT_FOLLOWERS.REMOTE_ACTOR_ID.eq(remoteActorId))
            .and(activityCondition)
            .execute() > 0
    }

    override fun findFolloweeUsername(
        followerActorUri: String,
        followActivityUri: String,
    ): String? = jooq.withConnection { dsl ->
        dsl.select(ACCOUNTS.USERNAME)
            .from(ACCOUNT_FOLLOWERS)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(ACCOUNT_FOLLOWERS.ACCOUNT_ID))
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(ACCOUNT_FOLLOWERS.REMOTE_ACTOR_ID))
            .where(REMOTE_ACTORS.ACTOR_URI.eq(followerActorUri))
            .and(ACCOUNT_FOLLOWERS.FOLLOW_ACTIVITY_URI.eq(followActivityUri))
            .fetchOne(ACCOUNTS.USERNAME)
    }

    override fun removeAccount(username: String): Int = jooq.withConnection { dsl ->
        val accountId = dsl.findAccountId(username) ?: return@withConnection 0
        dsl.deleteFrom(ACCOUNT_FOLLOWERS)
            .where(ACCOUNT_FOLLOWERS.ACCOUNT_ID.eq(accountId))
            .execute()
    }

    override fun removeRemoteActor(actorUri: String): Int = jooq.transaction { dsl ->
        val remoteActorId = dsl.findRemoteActorId(actorUri) ?: return@transaction 0
        val removedFollows = dsl.deleteFrom(ACCOUNT_FOLLOWERS)
            .where(ACCOUNT_FOLLOWERS.REMOTE_ACTOR_ID.eq(remoteActorId))
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
                .set(REMOTE_ACTORS.UPDATED_AT, StoredInstant.format(Instant.now()))
                .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
                .and(REMOTE_ACTORS.PUBLIC_KEY_PEM.ne(publicKeyPem))
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
                .set(REMOTE_ACTORS.UPDATED_AT, StoredInstant.format(Instant.now()))
                .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
                .execute()
        }
    }

    override fun listAcceptedFollowerActorUris(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = jooq.withConnection { dsl ->
        val afterCondition = if (after == null) DSL.noCondition() else REMOTE_ACTORS.ACTOR_URI.gt(after)
        dsl.select(REMOTE_ACTORS.ACTOR_URI)
            .from(ACCOUNT_FOLLOWERS)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(ACCOUNT_FOLLOWERS.ACCOUNT_ID))
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(ACCOUNT_FOLLOWERS.REMOTE_ACTOR_ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .and(ACCOUNT_FOLLOWERS.ACCEPTED_AT.isNotNull)
            .and(afterCondition)
            .orderBy(REMOTE_ACTORS.ACTOR_URI)
            .limit(limit)
            .fetch(REMOTE_ACTORS.ACTOR_URI)
            .filterNotNull()
    }

    override fun countAcceptedFollowers(username: String): Long = jooq.withConnection { dsl ->
        dsl.selectCount()
            .from(ACCOUNT_FOLLOWERS)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(ACCOUNT_FOLLOWERS.ACCOUNT_ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .and(ACCOUNT_FOLLOWERS.ACCEPTED_AT.isNotNull)
            .fetchOne(0, Long::class.java) ?: 0L
    }

    override fun findDeliveryInboxes(username: String): List<String> = jooq.withConnection { dsl ->
        val deliveryInbox = DSL.coalesce(REMOTE_ACTORS.SHARED_INBOX, REMOTE_ACTORS.INBOX)
        dsl.selectDistinct(deliveryInbox)
            .from(ACCOUNT_FOLLOWERS)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(ACCOUNT_FOLLOWERS.ACCOUNT_ID))
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(ACCOUNT_FOLLOWERS.REMOTE_ACTOR_ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .and(ACCOUNT_FOLLOWERS.ACCEPTED_AT.isNotNull)
            .fetch(deliveryInbox)
            .filterNotNull()
    }

    private fun DSLContext.findAccountId(username: String): Long? =
        select(ACCOUNTS.ID)
            .from(ACCOUNTS)
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .fetchOne(ACCOUNTS.ID)

    private fun DSLContext.findRemoteActorId(actorUri: String): Long? =
        select(REMOTE_ACTORS.ID)
            .from(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
            .fetchOne(REMOTE_ACTORS.ID)
}
