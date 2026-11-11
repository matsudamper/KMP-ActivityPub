package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import net.matsudamper.kmp.activitypub.repository.AccountBoostRepository
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.repository.CustomEmojiRecord
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.NotePinRepository
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.ProfileLink
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.StampCount
import net.matsudamper.kmp.activitypub.repository.StampRepository
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.slf4j.LoggerFactory

class UserProfileService(
    private val accounts: AccountRepository,
    private val remoteActors: RemoteActorRepository,
    private val notes: NoteRepository,
    private val favourites: FavouriteRepository,
    private val stamps: StampRepository,
    private val likes: LikeRepository,
    private val boosts: BoostRepository,
    private val accountBoosts: AccountBoostRepository,
    private val timeline: TimelineRepository,
    private val pins: NotePinRepository,
    private val following: FollowingService,
    private val remoteProfiles: RemoteUserProfiles?,
    private val localDomain: String,
) {
    private val logger = LoggerFactory.getLogger(UserProfileService::class.java)
    private val featuredFetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun profile(acct: String, viewer: AccountId?): UserProfile? {
        val normalizedAcct = acct.removePrefix("@")
        val username = normalizedAcct.substringBefore('@')
        if (username.isEmpty()) return null
        val host = normalizedAcct.substringAfter('@', "").ifEmpty { localDomain }
        if (host.equals(localDomain, ignoreCase = true)) {
            val account = accounts.findByUsername(username) ?: return null
            val detail = accounts.findProfileDetail(account.username)
            return UserProfile(
                user = SearchedUser(null, account.username, localDomain, true, account.displayName ?: account.username, null, listOf(), null),
                following = false,
                headerUrl = null,
                biography = detail?.summary,
                links = detail?.links.orEmpty(),
                posts = localProfilePosts(account.id, account.username, viewer),
            )
        }
        val storedActor = remoteActors.findByAcct(username, host)
        val followed = following.isFollowing(viewer, storedActor?.actorUri)
        if (followed) {
            val actor = checkNotNull(storedActor)
            return UserProfile(
                user = SearchedUser(actor.actorUri, actor.preferredUsername, host, false, actor.displayName ?: actor.preferredUsername, actor.iconUrl, actor.emojis, actor.updatedAt),
                following = true,
                headerUrl = remoteActors.findByUri(actor.actorUri)?.profile?.headerUrl,
                biography = null,
                links = listOf(),
                posts = withFeaturedPosts(storedPosts(checkNotNull(viewer), actor.actorUri), checkNotNull(viewer), actor.actorUri),
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
            headerUrl = remote.headerUrl,
            biography = remote.biography?.let { htmlToPlainText(it) },
            links = listOf(),
            posts = withFeaturedPosts(
                posts = if (remoteFollowed) storedPosts(viewer, remote.user.actorUri) else fetchedPosts(viewer, profiles.recentNotes(remote.user.actorUri)),
                viewer = viewer,
                actorUri = remote.user.actorUri,
            ),
        )
    }

    /**
     * このサーバーのアカウントの投稿と、そのアカウントが付けたブーストを新しい順に並べる。ブーストは、ブーストした時刻の位置に置く。
     * 固定した投稿は、固定した順で先頭に置く
     */
    private fun localProfilePosts(accountId: AccountId, username: String, viewer: AccountId?): List<ProfilePost> {
        val pinnedNoteIds = pins.pinnedNotes(username)
        val pinnedNotes = notes.findByPublicIds(pinnedNoteIds.toSet()).let { found -> pinnedNoteIds.mapNotNull { found[it] } }
        val pinnedNoteIdSet = pinnedNoteIds.toSet()
        val recentNotes = notes.list(username, after = null, limit = PROFILE_POST_LIMIT).filterNot { it.publicId in pinnedNoteIdSet }
        val localNotes = pinnedNotes + recentNotes
        val localNoteIds = localNotes.map { it.publicId }.toSet()
        val favouriteCounts = favourites.counts(localNoteIds)
        val stampCounts = stamps.counts(localNoteIds)
        val likedNoteIds = if (viewer == null) setOf() else likes.likedLocalNotes(viewer, localNoteIds)
        val boostedNoteIds = if (viewer == null) setOf() else accountBoosts.boostedLocalNotes(viewer, localNoteIds)
        val boostCounts = boosts.counts(localNoteIds)
        // 個別投稿画面はホームタイムラインに載る投稿しか開けず、このサーバーの投稿で載るのは自分の投稿と自分のブーストだけ
        val ownProfile = accountId == viewer
        val notePosts = localNotes.associate {
            val member = timeline.localPosition(it.publicId.value)?.member
            it.publicId to ProfilePost(
                contentHtml = it.contentHtml,
                publishedAt = it.publishedAt.toString(),
                summary = null,
                sensitive = false,
                linkPreviewId = LOCAL_NOTE_ID_PREFIX + it.publicId.value,
                postId = if (ownProfile) member else null,
                deletableNoteId = if (ownProfile) it.publicId.value else null,
                pinned = it.publicId in pinnedNoteIdSet,
                pinnableNoteId = if (ownProfile) it.publicId else null,
                images = it.images.map { image -> TimelinePostImage.Local(publicId = image.publicId, description = image.description) },
                emojis = listOf(),
                favouriteCount = favouriteCounts[it.publicId],
                stamps = stampCounts[it.publicId].orEmpty(),
                like = if (viewer == null || member == null) null else ProfilePostLike(postId = member, likedByMe = it.publicId in likedNoteIds),
                boostedByMe = if (viewer == null || member == null) null else it.publicId in boostedNoteIds,
                reactionPostId = if (viewer == null) null else member,
                noteRef = TimelineNoteRef.Local(it.publicId),
                boostCount = boostCounts[it.publicId],
                boostedNoteAuthor = null,
            )
        }
        val boostPosts = timeline.accountBoosts(booster = accountId, viewer = viewer, limit = PROFILE_POST_LIMIT).mapNotNull { post ->
            val author = post.boostedNoteAuthor() ?: return@mapNotNull null
            Instant.ofEpochMilli(post.position.score) to post.toProfilePost(
                viewer = viewer,
                openable = ownProfile,
                boostedNoteAuthor = author,
            )
        }
        val timelinePosts = recentNotes.map { it.publishedAt to checkNotNull(notePosts[it.publicId]) } + boostPosts
        return pinnedNotes.map { checkNotNull(notePosts[it.publicId]) } +
            timelinePosts.sortedByDescending { it.first }.take(PROFILE_POST_LIMIT).map { it.second }
    }

    /**
     * [linkPreviewId] がこのサーバーの投稿を指すなら、その本文。指さないか、投稿が無ければ null
     */
    fun localNoteContentHtml(linkPreviewId: String): String? {
        if (!linkPreviewId.startsWith(LOCAL_NOTE_ID_PREFIX)) return null
        return notes.find(PublicNoteId(linkPreviewId.removePrefix(LOCAL_NOTE_ID_PREFIX)))?.contentHtml
    }

    /**
     * 相手がプロフィールに固定した投稿を、相手が並べた順で先頭に置く。固定は受け取って保存していないので、開くたびに相手のサーバーへ取りに行く。
     * 同じ投稿を保存していれば、いいねなどが付いた保存済みの方を使う。取れなければ固定した投稿なしで返す
     */
    private suspend fun withFeaturedPosts(posts: List<ProfilePost>, viewer: AccountId, actorUri: String): List<ProfilePost> {
        val featured = featuredNotes(actorUri)
        if (featured.isEmpty()) return posts
        val postsByNoteRef = posts.filter { it.boostedNoteAuthor == null }.associateBy { it.noteRef }
        val fetchedByNoteRef = fetchedPosts(viewer, featured.filterNot { TimelineNoteRef.Remote(it.objectUri) in postsByNoteRef }).associateBy { it.noteRef }
        val pinnedPosts = featured.mapNotNull { note ->
            val noteRef = TimelineNoteRef.Remote(note.objectUri)
            (postsByNoteRef[noteRef] ?: fetchedByNoteRef[noteRef])?.copy(pinned = true)
        }
        val pinnedNoteRefs = pinnedPosts.map { it.noteRef }.toSet()
        return pinnedPosts + posts.filterNot { it.boostedNoteAuthor == null && it.noteRef in pinnedNoteRefs }
    }

    /**
     * フォロー中の相手は保存済みの投稿だけで出せるので、相手のサーバーが遅くてもプロフィールを待たせないよう期限を切る。
     *
     * 取得はこの呼び出しから切り離して動かす。ソケットの読み取りはスレッドの割り込みで止まらないことがあり、
     * 同じコルーチンで待つと期限を過ぎても読み取りのタイムアウトまで戻れない
     */
    private suspend fun featuredNotes(actorUri: String): List<RemoteNoteInput> {
        val profiles = remoteProfiles ?: return listOf()
        val fetching = featuredFetchScope.async {
            try {
                profiles.featuredNotes(actorUri)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.warn("固定した投稿を取れなかった: $actorUri", exception)
                listOf()
            }
        }
        val featured = try {
            withTimeoutOrNull(FEATURED_POSTS_TIMEOUT) { fetching.await() }
        } finally {
            fetching.cancel()
        }
        if (featured == null) logger.warn("固定した投稿の取得が期限を過ぎた: $actorUri")
        return featured.orEmpty()
    }

    /**
     * フォロー中の相手の投稿は受け取って保存しているので、相手のサーバーには取りに行かない
     */
    private fun storedPosts(viewer: AccountId, actorUri: String): List<ProfilePost> =
        timeline.actorPosts(viewer, actorUri, limit = PROFILE_POST_LIMIT).mapNotNull { post ->
            val author = if (post.boostedBy == null) null else post.boostedNoteAuthor() ?: return@mapNotNull null
            post.toProfilePost(viewer = viewer, openable = true, boostedNoteAuthor = author)
        }

    /**
     * フォローしていない相手の投稿は受け取っていないので、相手のサーバーから取ったものを保存して、いいねやブーストできるようにする。
     * 保存できなかった投稿は、いいねもブーストもできないまま出す
     */
    private fun fetchedPosts(viewer: AccountId, notes: List<RemoteNoteInput>): List<ProfilePost> {
        val stored = timeline.storeFetchedNotes(viewer, notes)
        return notes.map { note ->
            // 個別投稿画面はホームタイムラインに載る投稿しか開けず、フォローしていない相手の投稿は載らない
            stored[note.objectUri]?.toProfilePost(viewer = viewer, openable = false, boostedNoteAuthor = null) ?: note.toUnstoredProfilePost()
        }
    }

    private fun RemoteNoteInput.toUnstoredProfilePost(): ProfilePost = ProfilePost(
        contentHtml = contentHtml,
        publishedAt = publishedAt.toString(),
        summary = summary,
        sensitive = sensitive,
        linkPreviewId = null,
        postId = null,
        deletableNoteId = null,
        pinned = false,
        pinnableNoteId = null,
        images = images,
        emojis = emojis,
        favouriteCount = null,
        stamps = listOf(),
        like = null,
        boostedByMe = null,
        reactionPostId = null,
        noteRef = TimelineNoteRef.Remote(objectUri),
        boostCount = null,
        boostedNoteAuthor = null,
    )

    /**
     * @param openable 個別投稿画面で開けるか
     */
    private fun TimelinePost.toProfilePost(viewer: AccountId?, openable: Boolean, boostedNoteAuthor: ProfilePostAuthor?): ProfilePost {
        val notePublicId = localPublicId
        return ProfilePost(
            contentHtml = contentHtml,
            publishedAt = publishedAt.toString(),
            summary = summary,
            sensitive = sensitive,
            linkPreviewId = when {
                notePublicId != null -> LOCAL_NOTE_ID_PREFIX + notePublicId.value

                // OGP はタイムラインの投稿として引くので、見ている人のタイムラインで開けない投稿は引けない
                openable -> position.member

                else -> null
            },
            postId = if (openable) position.member else null,
            // ブーストとして載せた投稿は、ブーストされた元の投稿を消す場所ではない
            deletableNoteId = if (boostedBy == null) notePublicId?.value else null,
            pinned = false,
            pinnableNoteId = null,
            images = images,
            emojis = emojis,
            favouriteCount = favouriteCount,
            stamps = stamps,
            like = if (viewer == null) null else ProfilePostLike(postId = reactionMember, likedByMe = likedByMe),
            boostedByMe = if (viewer == null) null else boostedByMe,
            reactionPostId = if (viewer == null) null else reactionMember,
            noteRef = noteRef,
            boostCount = boostCount,
            boostedNoteAuthor = boostedNoteAuthor,
        )
    }

    /**
     * @return 他のサーバーの投稿者のホストが分からなければ null
     */
    private fun TimelinePost.boostedNoteAuthor(): ProfilePostAuthor? {
        val authorHost = if (actorUri == null) localDomain else URI(actorUri).host ?: return null
        return ProfilePostAuthor(
            actorUri = actorUri,
            host = authorHost,
            username = username,
            displayName = displayName,
            iconUrl = actorIconUrl,
            displayNameEmojis = actorEmojis,
            updatedAt = actorUpdatedAt,
        )
    }

    private companion object {
        const val PROFILE_POST_LIMIT = 20

        val FEATURED_POSTS_TIMEOUT: Duration = 3.seconds

        /**
         * このサーバーの投稿は公開されているので、タイムラインの id ではなく公開 id で OGP を引けるようにする
         */
        const val LOCAL_NOTE_ID_PREFIX = "note:"
    }
}

interface RemoteUserProfiles {
    suspend fun find(username: String, host: String): RemoteUserProfile?

    suspend fun findByActorUri(actorUri: String): RemoteUserProfile?

    /**
     * 相手のサーバーから取った、公開している最近の投稿。新しい順
     */
    suspend fun recentNotes(actorUri: String): List<RemoteNoteInput>

    /**
     * 相手がプロフィールに固定した投稿。相手が並べた順
     */
    suspend fun featuredNotes(actorUri: String): List<RemoteNoteInput>
}

/**
 * @param headerUrl ヘッダー画像の取得元。中継する前の相手の URL
 */
data class RemoteUserProfile(
    val user: RemoteUser,
    val headerUrl: String?,
    val biography: String?,
)

/**
 * @param headerUrl ヘッダー画像の取得元。中継する前の相手の URL。このサーバーのユーザーは設定できないので null
 * @param biography プレーンテキスト
 * @param links 他のサーバーのユーザーのものは受け取っていないので空
 */
data class UserProfile(
    val user: SearchedUser,
    val following: Boolean,
    val headerUrl: String?,
    val biography: String?,
    val links: List<ProfileLink>,
    val posts: List<ProfilePost>,
)

/**
 * @param linkPreviewId UserInfo.linkPreviews に渡せる id。OGP を取れない投稿は null
 * @param deletableNoteId 見ているアカウントの投稿なら deleteNote に渡す id。消せない投稿は null
 * @param pinned プロフィールの先頭に固定されているか
 * @param pinnableNoteId 見ているアカウントのプロフィールの投稿なら setPinned に渡す id。固定できない投稿は null
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
    val pinned: Boolean,
    val pinnableNoteId: PublicNoteId?,
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
 * @param actorUri 他のサーバーの投稿者のアクター。このサーバーのアカウントは null
 * @param username 相手の preferredUsername。無ければアクターの URI
 * @param iconUrl 他のサーバーの投稿者のプロフィール画像の URL。無いか、このサーバーのアカウントなら null
 * @param updatedAt 他のサーバーの投稿者の情報を最後に受け取った時刻。このサーバーのアカウントは null
 */
data class ProfilePostAuthor(
    val actorUri: String?,
    val host: String,
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
