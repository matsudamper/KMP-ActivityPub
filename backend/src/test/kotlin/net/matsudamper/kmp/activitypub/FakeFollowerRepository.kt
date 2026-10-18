package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.PendingFollowAccept
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord

internal class FakeFollowerRepository(
    private val accountRepository: FakeAccountRepository,
) : FollowerRepository {
    private val actors = mutableMapOf<String, RemoteActorRecord>()
    private val follows = mutableListOf<Follow>()
    private val accepts = mutableListOf<Accept>()

    override fun record(
        username: String,
        follower: RemoteActorRecord,
        followActivityUri: String,
        receivedAt: Instant,
        acceptBody: String,
        acceptNextAttemptAt: Instant,
    ): Boolean {
        val followee = accountRepository.findByUsername(username)?.username ?: return false
        actors[follower.actorUri] = follower
        val existing = follows.find(followee, follower.actorUri)
        if (existing != null) follows.remove(existing)
        follows += Follow(
            username = followee,
            actorUri = follower.actorUri,
            followActivityUri = followActivityUri,
            acceptedAt = existing?.acceptedAt,
        )
        accepts.removeAll { it.username == followee && it.actorUri == follower.actorUri }
        accepts += Accept(username = followee, actorUri = follower.actorUri, body = acceptBody, attempts = 0, nextAttemptAt = acceptNextAttemptAt)
        return true
    }

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean {
        val target = follows.find(username, followerActorUri)
            ?.takeIf { followActivityUri == null || it.followActivityUri == followActivityUri }
            ?: return false
        removeFollow(target)
        return true
    }

    override fun findFolloweeUsername(
        followerActorUri: String,
        followActivityUri: String,
    ): String? = follows.firstOrNull { it.actorUri == followerActorUri && it.followActivityUri == followActivityUri }?.username

    override fun removeAccount(username: String): Int {
        val targets = follows.filter { it.username == username.lowercase() }
        targets.forEach { removeFollow(it) }
        return targets.size
    }

    override fun removeRemoteActor(actorUri: String): Int {
        val targets = follows.filter { it.actorUri == actorUri }
        targets.forEach { removeFollow(it) }
        actors.remove(actorUri)
        return targets.size
    }

    override fun findPublicKeyPem(actorUri: String): String? = actors[actorUri]?.publicKeyPem

    override fun updatePublicKeyPem(
        actorUri: String,
        publicKeyPem: String,
    ) {
        actors[actorUri]?.let { actors[actorUri] = it.copy(publicKeyPem = publicKeyPem) }
    }

    override fun updateProfile(
        actorUri: String,
        profile: RemoteActorProfileRecord,
    ) {
        actors[actorUri]?.let { actors[actorUri] = it.copy(profile = profile) }
    }

    override fun listAccepted(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = acceptedActorUris(username).filter { after == null || it > after }.take(limit)

    override fun countAccepted(username: String): Long = acceptedActorUris(username).size.toLong()

    override fun deliveryTargets(username: String): List<String> =
        acceptedActorUris(username).map { checkNotNull(actors[it]).let { actor -> actor.sharedInbox ?: actor.inbox } }.distinct().sorted()

    override fun findDueAccepts(
        now: Instant,
        limit: Int,
    ): List<PendingFollowAccept> =
        accepts.filter { it.nextAttemptAt <= now }.sortedBy { it.nextAttemptAt }.take(limit).map { accept ->
            PendingFollowAccept(
                followeeUsername = accept.username,
                followerActorUri = accept.actorUri,
                followerInbox = checkNotNull(actors[accept.actorUri]).inbox,
                body = accept.body,
                attempts = accept.attempts,
            )
        }

    override fun markAccepted(
        accept: PendingFollowAccept,
        acceptedAt: Instant,
    ) {
        if (!accepts.removeAll { it.matches(accept) }) return
        val follow = checkNotNull(follows.find(accept.followeeUsername, accept.followerActorUri))
        follows[follows.indexOf(follow)] = follow.copy(acceptedAt = follow.acceptedAt ?: acceptedAt)
    }

    override fun postponeAccept(
        accept: PendingFollowAccept,
        nextAttemptAt: Instant,
    ) {
        val index = accepts.indexOfFirst { it.matches(accept) }.takeIf { it >= 0 } ?: return
        accepts[index] = accepts[index].copy(attempts = accept.attempts + 1, nextAttemptAt = nextAttemptAt)
    }

    override fun giveUpAccept(accept: PendingFollowAccept) {
        if (!accepts.removeAll { it.matches(accept) }) return
        follows.removeAll { it.username == accept.followeeUsername && it.actorUri == accept.followerActorUri && it.acceptedAt == null }
    }

    private fun acceptedActorUris(username: String): List<String> =
        follows.filter { it.username == username.lowercase() && it.acceptedAt != null }.map { it.actorUri }.sorted()

    private fun removeFollow(follow: Follow) {
        follows.remove(follow)
        accepts.removeAll { it.username == follow.username && it.actorUri == follow.actorUri }
    }

    private fun List<Follow>.find(
        username: String,
        actorUri: String,
    ): Follow? = firstOrNull { it.username == username.lowercase() && it.actorUri == actorUri }

    private data class Follow(
        val username: String,
        val actorUri: String,
        val followActivityUri: String,
        val acceptedAt: Instant?,
    )

    private data class Accept(
        val username: String,
        val actorUri: String,
        val body: String,
        val attempts: Int,
        val nextAttemptAt: Instant,
    ) {
        fun matches(pending: PendingFollowAccept): Boolean =
            username == pending.followeeUsername && actorUri == pending.followerActorUri && body == pending.body
    }
}
