package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.time.Clock
import kotlinx.coroutines.CancellationException
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.slf4j.LoggerFactory

class FollowingService(
    private val accounts: AccountRepository,
    private val following: FollowingRepository,
    private val delivery: FollowDelivery,
    private val invalidator: TimelineInvalidator,
    private val localDomain: String,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(FollowingService::class.java)

    fun isFollowing(accountId: AccountId?, actorUri: String?): Boolean =
        accountId != null && actorUri != null && following.contains(accountId, actorUri)

    suspend fun setFollowing(accountId: AccountId?, actorUri: String, followed: Boolean): Boolean {
        if (accountId == null || accounts.findById(accountId) == null) return false
        val uri = runCatching { URI(actorUri) }.getOrNull() ?: return false
        if (uri.scheme !in listOf("https", "http") || uri.host == null || uri.host.equals(localDomain, ignoreCase = true)) return false
        if (!followed) {
            val followId = following.followId(accountId, actorUri)
            if (following.remove(accountId, actorUri)) {
                invalidator.invalidate(accountId)
                try {
                    delivery.send(accountId, actorUri, false, followId)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    logger.warn("ローカルのフォローを解除したが Undo を積めなかった: $actorUri", failure)
                }
            }
        } else if (following.add(accountId, actorUri, clock.instant())) {
            try {
                delivery.send(accountId, actorUri, true, null)
            } catch (failure: Exception) {
                following.remove(accountId, actorUri)
                throw failure
            }
            invalidator.invalidate(accountId)
        }
        return true
    }
}

interface FollowDelivery {
    suspend fun send(accountId: AccountId, actorUri: String, followed: Boolean, followId: String?)
}

fun interface TimelineInvalidator {
    fun invalidate(accountId: AccountId)
}
