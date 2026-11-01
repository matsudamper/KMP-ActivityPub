package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

interface FollowingRepository {
    fun followId(accountId: AccountId, actorUri: String): String?
    fun recordActivity(accountId: AccountId, actorUri: String, followId: String)
    fun respondToFollow(actorUri: String, followId: String, acceptedAt: Instant?)
    fun contains(accountId: AccountId, actorUri: String): Boolean
    fun add(accountId: AccountId, actorUri: String, createdAt: Instant): Boolean
    fun remove(accountId: AccountId, actorUri: String): Boolean

    /**
     * フォローした新しい順に、[after] より後ろから [limit] 件まで返す
     */
    fun list(accountId: AccountId, after: ActorListPosition?, limit: Int): List<ListedActor>

    fun count(accountId: AccountId): Long
}
