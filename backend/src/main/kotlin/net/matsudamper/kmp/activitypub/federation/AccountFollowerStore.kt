package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.kotpub.follower.FollowerStore

internal class AccountFollowerStore(
    private val followerRepository: FollowerRepository,
) : FollowerStore {
    override fun record(
        username: String,
        follower: RemoteActor,
        followActivityUri: String,
        receivedAt: Instant,
        acceptBody: String,
    ): Boolean = followerRepository.record(
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
        acceptNextAttemptAt = receivedAt,
    )

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
    ): List<String> = followerRepository.listAccepted(username = username, after = after, limit = limit)

    override fun count(username: String): Long = followerRepository.countAccepted(username)

    override fun deliveryTargets(username: String): List<String> = followerRepository.deliveryTargets(username)

    private fun RemoteActorProfile.toRecord(): RemoteActorProfileRecord =
        RemoteActorProfileRecord(
            preferredUsername = preferredUsername,
            displayName = displayName,
            profileUrl = profileUrl,
            iconUrl = iconUrl,
        )
}
