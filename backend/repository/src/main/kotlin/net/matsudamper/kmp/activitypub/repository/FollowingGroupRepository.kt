package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

/**
 * 購読した相手を仕分けるグループ。どのグループにも入っていない購読はホームに入る
 */
interface FollowingGroupRepository {
    /**
     * 作った順に返す
     */
    fun list(accountId: AccountId): List<FollowingGroup>

    /**
     * 同じ名前のグループが既にあれば、それを返す
     */
    fun create(accountId: AccountId, name: String, createdAt: Instant): FollowingGroup

    fun exists(accountId: AccountId, groupId: FollowingGroupId): Boolean
}

data class FollowingGroup(
    val id: FollowingGroupId,
    val name: String,
)
