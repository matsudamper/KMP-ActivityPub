package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.EarlyUndoneLikeRepository
import net.matsudamper.kotpub.inbox.EarlyUndoneLikes

class RepositoryEarlyUndoneLikes(
    private val repository: EarlyUndoneLikeRepository,
) : EarlyUndoneLikes {
    override fun remember(
        actorUri: String,
        activityUri: String,
        expiresAt: Instant,
    ) {
        repository.remember(actorUri = actorUri, activityUri = activityUri, expiresAt = expiresAt)
    }

    override fun isRemembered(
        actorUri: String,
        activityUri: String,
        now: Instant,
    ): Boolean = repository.isRemembered(actorUri = actorUri, activityUri = activityUri, now = now)
}
