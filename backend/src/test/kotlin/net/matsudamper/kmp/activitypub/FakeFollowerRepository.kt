package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord

/**
 * フォロワーのいない状態だけを返す。配信先だけは [deliveryInboxes] で足せる
 */
internal class FakeFollowerRepository : FollowerRepository {
    val deliveryInboxes = mutableListOf<String>()

    override fun record(
        username: String,
        follower: RemoteActorRecord,
        followActivityUri: String,
        receivedAt: Instant,
        acceptBody: String,
    ): Boolean = false

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = false

    override fun findFolloweeUsername(
        followerActorUri: String,
        followActivityUri: String,
    ): String? = null

    override fun removeAccount(username: String): Int = 0

    override fun removeRemoteActor(actorUri: String): Int = 0

    override fun findPublicKeyPem(actorUri: String): String? = null

    override fun updatePublicKeyPem(
        actorUri: String,
        publicKeyPem: String,
    ) = Unit

    override fun updateProfile(
        actorUri: String,
        profile: RemoteActorProfileRecord,
    ) = Unit

    override fun listAcceptedFollowerActorUris(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = listOf()

    override fun countAcceptedFollowers(username: String): Long = 0

    override fun findDeliveryInboxes(username: String): List<String> = deliveryInboxes.toList()
}
