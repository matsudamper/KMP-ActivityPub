package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.time.Clock
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

class FollowingService(
    private val accounts: AccountRepository,
    private val following: FollowingRepository,
    private val delivery: FollowDelivery,
    private val localDomain: String,
    private val clock: Clock,
) {
    fun isFollowing(accountId: AccountId?, actorUri: String?): Boolean =
        accountId != null && actorUri != null && following.contains(accountId, actorUri)

    fun setFollowing(accountId: AccountId?, actorUri: String, followed: Boolean): Boolean {
        if (accountId == null || accounts.findById(accountId) == null) return false
        val uri = runCatching { URI(actorUri) }.getOrNull() ?: return false
        if (uri.scheme !in listOf("https", "http") || uri.host == null || uri.host.equals(localDomain, ignoreCase = true)) return false
        val changed = if (followed) {
            following.add(accountId, actorUri, clock.instant())
        } else {
            following.remove(accountId, actorUri)
        }
        if (changed) delivery.send(accountId, actorUri, followed)
        return true
    }
}

interface FollowDelivery {
    fun send(accountId: AccountId, actorUri: String, followed: Boolean)
}

/** 第一段階では内部保存だけを行うため、Follow / Undo を相手の inbox へ送らない。 */
object NoOpFollowDelivery : FollowDelivery {
    override fun send(accountId: AccountId, actorUri: String, followed: Boolean) = Unit
}
