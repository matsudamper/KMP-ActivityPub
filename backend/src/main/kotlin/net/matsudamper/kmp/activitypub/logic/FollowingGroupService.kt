package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.FollowingGroup
import net.matsudamper.kmp.activitypub.repository.FollowingGroupRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

/**
 * 購読した相手をグループに仕分ける。グループに入れていない購読はホームに入る
 */
class FollowingGroupService(
    private val accounts: AccountRepository,
    private val groups: FollowingGroupRepository,
    private val following: FollowingRepository,
    private val followingService: FollowingService,
    private val clock: Clock,
) {
    fun groups(accountId: AccountId): List<FollowingGroup> = groups.list(accountId)

    /**
     * 購読していないか、ホームに入っていれば null
     */
    fun groupIdOf(accountId: AccountId?, actorUri: String?): FollowingGroupId? {
        if (accountId == null || actorUri == null) return null
        return following.groupIdOf(accountId, actorUri)
    }

    /**
     * 投稿を載せた購読先。ブーストならブーストした相手の購読先。自分の投稿などホームに入るものは null
     */
    fun groupIdOfPost(accountId: AccountId, post: TimelinePost): FollowingGroupId? =
        groupIdOf(accountId, post.boostedBy?.actorUri ?: post.actorUri)

    fun createGroup(accountId: AccountId?, name: String): CreateFollowingGroupResult {
        if (accountId == null || accounts.findById(accountId) == null) return CreateFollowingGroupResult.NotLoggedIn
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName.length > MAX_NAME_LENGTH) return CreateFollowingGroupResult.InvalidName
        return CreateFollowingGroupResult.Created(groups.create(accountId, trimmedName, clock.instant()))
    }

    /**
     * 購読するときは [groupId] に入れる。既に購読していれば入れ直すだけ。
     *
     * @param groupId null ならホーム。購読をやめるときは使わない
     * @return 未ログイン・不正な相手・他のアカウントのグループなら false
     */
    suspend fun setFollowing(accountId: AccountId?, actorUri: String, followed: Boolean, groupId: FollowingGroupId?): Boolean {
        if (followed && accountId != null && groupId != null && !groups.exists(accountId, groupId)) return false
        if (!followingService.setFollowing(accountId, actorUri, followed)) return false
        if (followed && accountId != null) following.setGroup(accountId, actorUri, groupId)
        return true
    }

    companion object {
        const val MAX_NAME_LENGTH = 30
    }
}

sealed interface CreateFollowingGroupResult {
    data class Created(val group: FollowingGroup) : CreateFollowingGroupResult
    data object NotLoggedIn : CreateFollowingGroupResult

    /**
     * 空か長すぎる
     */
    data object InvalidName : CreateFollowingGroupResult
}
