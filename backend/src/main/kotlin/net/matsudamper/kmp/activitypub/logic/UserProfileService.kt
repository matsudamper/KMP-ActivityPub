package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

class UserProfileService(
    private val accounts: AccountRepository,
    private val remoteActors: RemoteActorRepository,
    private val notes: NoteRepository,
    private val timeline: TimelineRepository,
    private val following: FollowingService,
    private val remoteProfiles: RemoteUserProfiles?,
    private val localDomain: String,
) {
    suspend fun profile(acct: String, viewer: AccountId?): UserProfile? {
        val normalizedAcct = acct.removePrefix("@")
        val username = normalizedAcct.substringBefore('@')
        if (username.isEmpty()) return null
        val host = normalizedAcct.substringAfter('@', "").ifEmpty { localDomain }
        if (host.equals(localDomain, ignoreCase = true)) {
            val account = accounts.findByUsername(username) ?: return null
            return UserProfile(
                user = SearchedUser(null, account.username, localDomain, true, account.displayName ?: account.username, null, null),
                following = false,
                biography = null,
                posts = notes.list(account.username, after = null, limit = PROFILE_POST_LIMIT).map { ProfilePost(it.contentHtml, it.publishedAt.toString()) },
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
                posts = storedPosts(checkNotNull(viewer), actor.actorUri),
            )
        }
        if (viewer == null) return null
        val profiles = remoteProfiles ?: return null
        val remote = if (storedActor != null) profiles.findByActorUri(storedActor.actorUri) else profiles.find(username, host)
        if (remote == null) return null
        val actorHost = URI(remote.user.actorUri).host ?: return null
        val remoteFollowed = following.isFollowing(viewer, remote.user.actorUri)
        return UserProfile(
            user = SearchedUser(remote.user.actorUri, remote.user.username, actorHost, false, remote.user.displayName ?: remote.user.username, remote.user.iconUrl, null),
            following = remoteFollowed,
            biography = remote.biography,
            posts = if (remoteFollowed) storedPosts(viewer, remote.user.actorUri) else profiles.recentPosts(remote.user.actorUri),
        )
    }

    /**
     * フォロー中の相手の投稿は受け取って保存しているので、相手のサーバーには取りに行かない
     */
    private fun storedPosts(viewer: AccountId, actorUri: String): List<ProfilePost> =
        timeline.actorPosts(viewer, actorUri, limit = PROFILE_POST_LIMIT).map { it.toProfilePost() }

    private fun TimelinePost.toProfilePost(): ProfilePost {
        // タイムラインと同じく、閲覧注意の投稿は本文を出さない
        val shownHtml = if (sensitive) summary ?: SENSITIVE_PLACEHOLDER else contentHtml
        return ProfilePost(contentHtml = shownHtml, publishedAt = publishedAt.toString())
    }

    private companion object {
        const val PROFILE_POST_LIMIT = 20
        const val SENSITIVE_PLACEHOLDER = "閲覧注意の投稿"
    }
}

interface RemoteUserProfiles {
    suspend fun find(username: String, host: String): RemoteUserProfile?

    suspend fun findByActorUri(actorUri: String): RemoteUserProfile?

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
