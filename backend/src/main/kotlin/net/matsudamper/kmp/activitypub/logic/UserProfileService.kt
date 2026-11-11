package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.repository.CustomEmojiRecord
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.ProfileLink
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.StampCount
import net.matsudamper.kmp.activitypub.repository.StampRepository
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class UserProfileService(
    private val accounts: AccountRepository,
    private val remoteActors: RemoteActorRepository,
    private val notes: NoteRepository,
    private val favourites: FavouriteRepository,
    private val stamps: StampRepository,
    private val likes: LikeRepository,
    private val boosts: BoostRepository,
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
            val localNotes = notes.list(account.username, after = null, limit = PROFILE_POST_LIMIT)
            val localNoteIds = localNotes.map { it.publicId }.toSet()
            val favouriteCounts = favourites.counts(localNoteIds)
            val stampCounts = stamps.counts(localNoteIds)
            val likedNoteIds = if (viewer == null) setOf() else likes.likedLocalNotes(viewer, localNoteIds)
            val boostCounts = boosts.counts(localNoteIds)
            // 個別投稿画面はホームタイムラインに載る投稿しか開けず、このサーバーの投稿で載るのは自分のものだけ
            val ownProfile = account.id == viewer
            val detail = accounts.findProfileDetail(account.username)
            return UserProfile(
                user = SearchedUser(null, account.username, localDomain, true, account.displayName ?: account.username, null, listOf(), null),
                following = false,
                biography = detail?.summary,
                links = detail?.links.orEmpty(),
                posts = localNotes.map {
                    val member = timeline.localPosition(it.publicId.value)?.member
                    ProfilePost(
                        contentHtml = it.contentHtml,
                        publishedAt = it.publishedAt.toString(),
                        summary = null,
                        sensitive = false,
                        linkPreviewId = LOCAL_NOTE_ID_PREFIX + it.publicId.value,
                        postId = if (ownProfile) member else null,
                        deletableNoteId = if (ownProfile) it.publicId.value else null,
                        images = it.images.map { image -> TimelinePostImage.Local(publicId = image.publicId, description = image.description) },
                        emojis = listOf(),
                        favouriteCount = favouriteCounts[it.publicId],
                        stamps = stampCounts[it.publicId].orEmpty(),
                        like = if (viewer == null || member == null) null else ProfilePostLike(postId = member, likedByMe = it.publicId in likedNoteIds),
                        boostedByMe = null,
                        reactionPostId = if (viewer == null) null else member,
                        noteRef = TimelineNoteRef.Local(it.publicId),
                        boostCount = boostCounts[it.publicId],
                        boostedNoteAuthor = null,
                    )
                },
            )
        }
        val storedActor = remoteActors.findByAcct(username, host)
        val followed = following.isFollowing(viewer, storedActor?.actorUri)
        if (followed) {
            val actor = checkNotNull(storedActor)
            return UserProfile(
                user = SearchedUser(actor.actorUri, actor.preferredUsername, host, false, actor.displayName ?: actor.preferredUsername, actor.iconUrl, actor.emojis, actor.updatedAt),
                following = true,
                biography = null,
                links = listOf(),
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
            user = SearchedUser(
                remote.user.actorUri,
                remote.user.username,
                actorHost,
                false,
                remote.user.displayName ?: remote.user.username,
                remote.user.iconUrl,
                remote.user.displayNameEmojis,
                null,
            ),
            following = remoteFollowed,
            biography = remote.biography?.let { htmlToPlainText(it) },
            links = listOf(),
            posts = if (remoteFollowed) storedPosts(viewer, remote.user.actorUri) else profiles.recentPosts(remote.user.actorUri),
        )
    }

    /**
     * [linkPreviewId] がこのサーバーの投稿を指すなら、その本文。指さないか、投稿が無ければ null
     */
    fun localNoteContentHtml(linkPreviewId: String): String? {
        if (!linkPreviewId.startsWith(LOCAL_NOTE_ID_PREFIX)) return null
        return notes.find(PublicNoteId(linkPreviewId.removePrefix(LOCAL_NOTE_ID_PREFIX)))?.contentHtml
    }

    /**
     * フォロー中の相手の投稿は受け取って保存しているので、相手のサーバーには取りに行かない
     */
    private fun storedPosts(viewer: AccountId, actorUri: String): List<ProfilePost> =
        timeline.actorPosts(viewer, actorUri, limit = PROFILE_POST_LIMIT).map { it.toProfilePost() }

    private fun TimelinePost.toProfilePost(): ProfilePost {
        return ProfilePost(
            contentHtml = contentHtml,
            publishedAt = publishedAt.toString(),
            summary = summary,
            sensitive = sensitive,
            linkPreviewId = position.member,
            postId = position.member,
            deletableNoteId = localPublicId?.value,
            images = images,
            emojis = emojis,
            favouriteCount = favouriteCount,
            stamps = stamps,
            like = ProfilePostLike(postId = reactionMember, likedByMe = likedByMe),
            boostedByMe = boostedByMe,
            reactionPostId = reactionMember,
            noteRef = noteRef,
            boostCount = boostCount,
            boostedNoteAuthor = if (boostedBy == null) null else boostedNoteAuthor(),
        )
    }

    private fun TimelinePost.boostedNoteAuthor(): ProfilePostAuthor {
        return ProfilePostAuthor(
            actorUri = checkNotNull(actorUri),
            username = username,
            displayName = displayName,
            iconUrl = actorIconUrl,
            displayNameEmojis = actorEmojis,
            updatedAt = actorUpdatedAt,
        )
    }

    private companion object {
        const val PROFILE_POST_LIMIT = 20

        /**
         * このサーバーの投稿は公開されているので、タイムラインの id ではなく公開 id で OGP を引けるようにする
         */
        const val LOCAL_NOTE_ID_PREFIX = "note:"
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

/**
 * @param biography プレーンテキスト
 * @param links 他のサーバーのユーザーのものは受け取っていないので空
 */
data class UserProfile(
    val user: SearchedUser,
    val following: Boolean,
    val biography: String?,
    val links: List<ProfileLink>,
    val posts: List<ProfilePost>,
)

/**
 * @param linkPreviewId UserInfo.linkPreviews に渡せる id。OGP を取れない投稿は null
 * @param deletableNoteId 見ているアカウントの投稿なら deleteNote に渡す id。消せない投稿は null
 * @param images URL にはしていない画像
 * @param favouriteCount 他のサーバーから届いたお気に入りの数。他のサーバーの投稿は数を受け取らないので null
 * @param stamps 他のサーバーから届いたスタンプ。他のサーバーの投稿は受け取らないので空
 * @param like いいねできない投稿は null
 * @param boostedByMe 見ているアカウントがブーストしているか。ログインしていないか、ブーストできない投稿は null。付け外しには [reactionPostId] を渡す
 * @param reactionPostId いいねやブーストを付け外しするときに渡す、タイムラインの投稿の id。ブーストした投稿は、ブーストされた元の投稿を指す。
 *   ログインしていないか、付け外しできない投稿は null
 * @param noteRef 投稿を外から指すための元
 * @param boostCount 他のサーバーから届いたブーストの数。他のサーバーの投稿は数を受け取らないので null
 * @param boostedNoteAuthor プロフィールの本人がブーストした投稿なら、その投稿者。本人の投稿は null
 */
data class ProfilePost(
    val contentHtml: String,
    val publishedAt: String,
    val summary: String?,
    val sensitive: Boolean,
    val linkPreviewId: String?,
    val postId: String?,
    val deletableNoteId: String?,
    val images: List<TimelinePostImage>,
    val emojis: List<CustomEmojiRecord>,
    val favouriteCount: Long?,
    val stamps: List<StampCount>,
    val noteRef: TimelineNoteRef,
    val boostCount: Long?,
    val like: ProfilePostLike?,
    val boostedByMe: Boolean?,
    val reactionPostId: String?,
    val boostedNoteAuthor: ProfilePostAuthor?,
)

/**
 * @param username 相手の preferredUsername。無ければアクターの URI
 * @param iconUrl プロフィール画像の URL。無ければ null
 * @param updatedAt 相手の情報を最後に受け取った時刻
 */
data class ProfilePostAuthor(
    val actorUri: String,
    val username: String,
    val displayName: String?,
    val iconUrl: String?,
    val displayNameEmojis: List<CustomEmojiRecord>,
    val updatedAt: Instant?,
)

/**
 * @param postId いいねを付け外しするときに渡す、タイムラインの投稿の id
 */
data class ProfilePostLike(
    val postId: String,
    val likedByMe: Boolean,
)
