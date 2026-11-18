package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kotpub.follower.FollowingStore

internal class AccountFollowingStore(
    private val followingRepository: FollowingRepository,
) : FollowingStore {
    override fun list(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = followingRepository.listAcceptedActorUris(username = username, after = after, limit = limit)

    override fun count(username: String): Long = followingRepository.countAccepted(username)
}
