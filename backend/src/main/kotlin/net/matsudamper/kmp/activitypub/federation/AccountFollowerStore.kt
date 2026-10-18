package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.kotpub.follower.FollowerStore

/**
 * @param onAcceptEnqueued Accept を積んだ後に呼ぶ。相手の画面ではフォローが保留のまま待っているので、次の巡回を待たずに送らせる
 */
internal class AccountFollowerStore(
    private val followerRepository: FollowerRepository,
    private val onAcceptEnqueued: () -> Unit,
) : FollowerStore {
    override fun record(
        username: String,
        follower: RemoteActor,
        followActivityUri: String,
        receivedAt: Instant,
        acceptBody: String,
    ): Boolean {
        val recorded = followerRepository.record(
            username = username,
            follower = RemoteActorRecord(
                actorUri = follower.actorId,
                inbox = follower.inbox,
                sharedInbox = follower.sharedInbox,
                publicKeyPem = follower.publicKeyPem,
                profile = follower.profile.toRecord(),
            ),
            followActivityUri = followActivityUri,
            receivedAt = receivedAt,
            acceptBody = acceptBody,
        )
        if (recorded) onAcceptEnqueued()
        return recorded
    }

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = followerRepository.remove(
        username = username,
        followerActorUri = followerActorUri,
        followActivityUri = followActivityUri,
    )

    override fun findFolloweeUsername(
        followerActorUri: String,
        followActivityUri: String,
    ): String? = followerRepository.findFolloweeUsername(
        followerActorUri = followerActorUri,
        followActivityUri = followActivityUri,
    )

    override fun removeAccount(username: String): Int = followerRepository.removeAccount(username)

    override fun removeRemoteActor(actorUri: String): Int = followerRepository.removeRemoteActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = followerRepository.findPublicKeyPem(actorUri)

    override fun rememberPublicKeyPem(
        actorUri: String,
        publicKeyPem: String,
    ) {
        followerRepository.updatePublicKeyPem(actorUri = actorUri, publicKeyPem = publicKeyPem)
    }

    override fun rememberProfile(
        actorUri: String,
        profile: RemoteActorProfile,
    ) {
        followerRepository.updateProfile(actorUri = actorUri, profile = profile.toRecord())
    }

    override fun list(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = followerRepository.listAcceptedFollowerActorUris(username = username, after = after, limit = limit)

    override fun count(username: String): Long = followerRepository.countAcceptedFollowers(username)

    override fun deliveryTargets(username: String): List<String> = followerRepository.findDeliveryInboxes(username)

    private fun RemoteActorProfile.toRecord(): RemoteActorProfileRecord =
        RemoteActorProfileRecord(
            preferredUsername = preferredUsername,
            displayName = displayName,
            profileUrl = profileUrl,
            iconUrl = iconUrl,
        )
}
