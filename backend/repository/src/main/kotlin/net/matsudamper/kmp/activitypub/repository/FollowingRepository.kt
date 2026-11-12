package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

interface FollowingRepository {
    fun followId(accountId: AccountId, actorUri: String): String?
    fun recordActivity(accountId: AccountId, actorUri: String, followId: String)
    fun recordActivityAndEnqueueFollow(accountId: AccountId, actorUri: String, followId: String, inbox: String, body: String, now: Instant): Boolean
    fun respondToFollow(actorUri: String, followId: String, acceptedAt: Instant?): Int
    fun contains(accountId: AccountId, actorUri: String): Boolean
    fun add(accountId: AccountId, actorUri: String, createdAt: Instant): Boolean
    fun remove(accountId: AccountId, actorUri: String): Boolean

    /**
     * 購読していないか、メインに入っていれば null
     */
    fun groupIdOf(accountId: AccountId, actorUri: String): FollowingGroupId?

    /**
     * @param groupId null ならメインに戻す
     * @return 購読していなければ false
     */
    fun setGroup(accountId: AccountId, actorUri: String, groupId: FollowingGroupId?): Boolean

    /**
     * フォローした新しい順に、[after] より後ろから [limit] 件まで返す
     */
    fun list(accountId: AccountId, after: ActorListPosition?, limit: Int): List<ListedActor>

    fun count(accountId: AccountId): Long
}
