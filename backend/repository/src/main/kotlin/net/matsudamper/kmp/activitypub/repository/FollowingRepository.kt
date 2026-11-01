package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

interface FollowingRepository {
    fun contains(accountId: AccountId, actorUri: String): Boolean
    fun add(accountId: AccountId, actorUri: String, createdAt: Instant): Boolean
    fun remove(accountId: AccountId, actorUri: String): Boolean

    /**
     * フォローした新しい順に、[after] より後ろから [limit] 件まで返す
     */
    fun list(accountId: AccountId, after: ActorListPosition?, limit: Int): List<ListedActor>

    fun count(accountId: AccountId): Long
}
