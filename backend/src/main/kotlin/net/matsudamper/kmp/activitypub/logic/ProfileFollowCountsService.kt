package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * プロフィールに出すフォロー・フォロワーの数。
 * 他のサーバーのユーザーは相手のサーバーへ取りに行くので、[UserProfileService.profile] とは別に読む
 */
class ProfileFollowCountsService(
    private val accounts: AccountRepository,
    private val following: FollowingRepository,
    private val followers: FollowerRepository,
    private val remoteActors: RemoteActorRepository,
    private val remoteProfiles: RemoteUserProfiles?,
    private val localDomain: String,
) {
    /**
     * ログインしていなければ、他のサーバーのユーザーは相手のサーバーへ取りに行かないので、保存済みのユーザーでも数は null
     *
     * @return ユーザーが見つからなければ null
     */
    suspend fun counts(acct: String, viewer: AccountId?): ProfileFollowCounts? {
        val target = ProfileAcct.parse(acct, localDomain) ?: return null
        if (target.isLocal) {
            val account = accounts.findByUsername(target.username) ?: return null
            return ProfileFollowCounts(
                following = following.count(account.id),
                followers = followers.countAcceptedFollowers(account.username),
            )
        }
        val storedActorUri = remoteActors.findByAcct(target.username, target.host)?.actorUri
        val profiles = remoteProfiles
        if (viewer == null || profiles == null) {
            return if (storedActorUri == null) null else ProfileFollowCounts(following = null, followers = null)
        }
        val actorUri = storedActorUri
            ?: profiles.find(target.username, target.host)?.user?.actorUri
            ?: return null
        return profiles.followCounts(actorUri)
    }
}

/**
 * @param following 他のサーバーのユーザーが数を出していないか、読めなければ null
 * @param followers 成立したフォローだけを数える。他のサーバーのユーザーが数を出していないか、読めなければ null
 */
data class ProfileFollowCounts(
    val following: Long?,
    val followers: Long?,
)
