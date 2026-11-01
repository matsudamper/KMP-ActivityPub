package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

class UserProfileService(
    private val accounts: AccountRepository,
    private val remoteActors: RemoteActorRepository,
    private val notes: NoteRepository,
    private val following: FollowingService,
    private val remoteProfiles: RemoteUserProfiles?,
    private val localDomain: String,
) {
    suspend fun profile(acct: String, viewer: AccountId?): UserProfile? {
        val username = acct.removePrefix("@").substringBefore('@')
        val host = acct.removePrefix("@").substringAfter('@', localDomain)
        if (host.equals(localDomain, ignoreCase = true)) {
            val account = accounts.findByUsername(username) ?: return null
            return UserProfile(
                user = SearchedUser(null, account.username, localDomain, true, account.displayName ?: account.username, null, null),
                following = false,
                biography = null,
                posts = notes.list(account.username, after = null, limit = 20).map { ProfilePost(it.contentHtml, it.publishedAt.toString()) },
            )
        }
        val storedActor = remoteActors.findByAcct(username, host)
        val followed = following.isFollowing(viewer, storedActor?.actorUri)
        if (followed) {
            val actor = checkNotNull(storedActor)
            return UserProfile(
                user = SearchedUser(actor.actorUri, actor.preferredUsername, host, false, actor.displayName ?: actor.preferredUsername, actor.iconUrl, actor.updatedAt),
                following = true,
                biography = null,
                // TODO: リモート投稿の受信・保存が実装されたら、保存済み投稿をここから取得する。
                posts = listOf(),
            )
        }
        if (viewer == null) return null
        val remote = remoteProfiles?.find(username, host) ?: return null
        val actorHost = URI(remote.user.actorUri).host ?: return null
        val remoteFollowed = following.isFollowing(viewer, remote.user.actorUri)
        return UserProfile(
            user = SearchedUser(remote.user.actorUri, remote.user.username, actorHost, false, remote.user.displayName ?: remote.user.username, remote.user.iconUrl, null),
            following = remoteFollowed,
            biography = remote.biography,
            posts = if (remoteFollowed) listOf() else remoteProfiles.recentPosts(remote.user.actorUri),
        )
    }
}

interface RemoteUserProfiles {
    suspend fun find(username: String, host: String): RemoteUserProfile?

    suspend fun recentPosts(actorUri: String): List<ProfilePost>
}

data class RemoteUserProfile(
    val user: RemoteUser,
    val biography: String?,
)

data class UserProfile(
    val user: SearchedUser,
    val following: Boolean,
    val biography: String?,
    val posts: List<ProfilePost>,
)

data class ProfilePost(
    val contentHtml: String,
    val publishedAt: String,
)
