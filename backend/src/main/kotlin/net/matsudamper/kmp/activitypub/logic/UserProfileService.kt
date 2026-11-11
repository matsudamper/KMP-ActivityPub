package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.time.Instant
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.repository.CustomEmojiRecord
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.repository.LikeRepository
import net.matsudamper.kmp.activitypub.repository.NotePinRepository
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
import org.slf4j.LoggerFactory

class UserProfileService(
    private val accounts: AccountRepository,
    private val remoteActors: RemoteActorRepository,
    private val notes: NoteRepository,
    private val favourites: FavouriteRepository,
    private val stamps: StampRepository,
    private val likes: LikeRepository,
    private val boosts: BoostRepository,
    private val timeline: TimelineRepository,
    private val pins: NotePinRepository,
    private val following: FollowingService,
    private val remoteProfiles: RemoteUserProfiles?,
    private val localDomain: String,
) {
    private val logger = LoggerFactory.getLogger(UserProfileService::class.java)
    private val remoteFetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun profile(acct: String, viewer: AccountId?): UserProfile? {
        val normalizedAcct = acct.removePrefix("@")
        val username = normalizedAcct.substringBefore('@')
        if (username.isEmpty()) return null
        val host = normalizedAcct.substringAfter('@', "").ifEmpty { localDomain }
        if (host.equals(localDomain, ignoreCase = true)) {
            val account = accounts.findByUsername(username) ?: return null
            val pinnedNoteIds = pins.pinnedNotes(account.username)
            val pinnedNotes = notes.findByPublicIds(pinnedNoteIds.toSet()).let { found -> pinnedNoteIds.mapNotNull { found[it] } }
            val pinnedNoteIdSet = pinnedNoteIds.toSet()
            val localNotes = pinnedNotes + notes.list(account.username, after = null, limit = PROFILE_POST_LIMIT).filterNot { it.publicId in pinnedNoteIdSet }
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
                headerUrl = null,
                biography = detail?.summary,
                links = detail?.links.orEmpty(),
                postsNextCursor = null,
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
                        pinned = it.publicId in pinnedNoteIdSet,
                        pinnableNoteId = if (ownProfile) it.publicId else null,
                        images = it.images.map { image -> TimelinePostImage.Local(publicId = image.publicId, description = image.description) },
                        emojis = listOf(),
                        favouriteCount = favouriteCounts[it.publicId],
                        stamps = stampCounts[it.publicId].orEmpty(),
                        like = if (viewer == null || member == null) null else ProfilePostLike(postId = member, likedByMe = it.publicId in likedNoteIds),
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
            val page = firstRemotePostsPage(viewer = checkNotNull(viewer), actorUri = actor.actorUri, followed = true)
            return UserProfile(
                user = SearchedUser(actor.actorUri, actor.preferredUsername, host, false, actor.displayName ?: actor.preferredUsername, actor.iconUrl, actor.emojis, actor.updatedAt),
                following = true,
                headerUrl = remoteActors.findByUri(actor.actorUri)?.profile?.headerUrl,
                biography = null,
                links = listOf(),
                posts = page.posts,
                postsNextCursor = page.nextCursor,
            )
        }
        if (viewer == null) return null
        val profiles = remoteProfiles ?: return null
        val remote = if (storedActor != null) profiles.findByActorUri(storedActor.actorUri) else profiles.find(username, host)
        if (remote == null) return null
        val actorHost = URI(remote.user.actorUri).host ?: return null
        val remoteFollowed = following.isFollowing(viewer, remote.user.actorUri)
        val page = firstRemotePostsPage(viewer = viewer, actorUri = remote.user.actorUri, followed = remoteFollowed)
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
            posts = page.posts,
            postsNextCursor = page.nextCursor,
        )
    }

    /**
     * 他のサーバーのユーザーのプロフィールの、[UserProfile.postsNextCursor] から先の投稿
     */
    suspend fun remotePosts(viewer: AccountId?, actorUri: String, cursor: ProfilePostCursor): RemoteProfilePostsResult {
        if (viewer == null) return RemoteProfilePostsResult.NotLoggedIn
        val followed = following.isFollowing(viewer, actorUri)
        val outboxPage = outboxPage(actorUri = actorUri, pageId = cursor.nextPageId) ?: return RemoteProfilePostsResult.Unreachable
        return RemoteProfilePostsResult.Found(mergedPage(viewer = viewer, actorUri = actorUri, followed = followed, outboxPage = outboxPage, before = cursor.before))
    }

    /**
     * [linkPreviewId] がこのサーバーの投稿を指すなら、その本文。指さないか、投稿が無ければ null
     */
    fun localNoteContentHtml(linkPreviewId: String): String? {
        if (!linkPreviewId.startsWith(LOCAL_NOTE_ID_PREFIX)) return null
        return notes.find(PublicNoteId(linkPreviewId.removePrefix(LOCAL_NOTE_ID_PREFIX)))?.contentHtml
    }

    /**
     * 相手のサーバーの outbox を主に並べ、保存済みの投稿で補う。保存済みの投稿は、いいねなどが付いた保存済みの方を使う。
     * outbox を読めなければ、保存済みの投稿だけを返して続きは読まない。
     * 固定した投稿は受け取って保存していないので、開くたびに相手のサーバーへ取りに行き、相手が並べた順で先頭に置く
     */
    private suspend fun firstRemotePostsPage(viewer: AccountId, actorUri: String, followed: Boolean): ProfilePostPage = coroutineScope {
        val featured = async { detached(FEATURED_POSTS_TIMEOUT, "固定した投稿", actorUri) { it.featuredPosts(actorUri) }.orEmpty() }
        val outboxPage = outboxPage(actorUri = actorUri, pageId = null)
        val page = if (outboxPage == null) {
            ProfilePostPage(posts = storedPosts(viewer = viewer, actorUri = actorUri, before = null), nextCursor = null)
        } else {
            mergedPage(viewer = viewer, actorUri = actorUri, followed = followed, outboxPage = outboxPage, before = null)
        }
        page.copy(posts = withFeaturedPosts(posts = page.posts, featured = withLikes(viewer, followed, featured.await())))
    }

    /**
     * @return 読めなければ null
     */
    private suspend fun outboxPage(actorUri: String, pageId: String?): RemoteProfilePostPage? =
        detached(OUTBOX_TIMEOUT, "outbox", actorUri) { it.recentPostsPage(actorUri, pageId) }

    /**
     * outbox の 1 ページに、その範囲の保存済みの投稿を混ぜる。outbox に載らないブーストやフォロワー限定の投稿は、保存済みの方にしか無い
     *
     * @param before 前のページで返した範囲の下端。最初のページは null
     */
    private fun mergedPage(viewer: AccountId, actorUri: String, followed: Boolean, outboxPage: RemoteProfilePostPage, before: Long?): ProfilePostPage {
        val outboxPosts = withLikes(viewer, followed, outboxPage.posts)
        // 続きがあるときは、このページの outbox の範囲より古い保存済みの投稿を次のページに回す。
        // outbox に読める投稿が無ければ、範囲が決まらないので保存済みの投稿も次に回す
        val lowest = if (outboxPage.nextPageId == null) null else outboxPosts.minOfOrNull { it.sortScore() } ?: before ?: Long.MAX_VALUE
        val outboxNoteRefs = outboxPosts.map { it.noteRef }.toSet()
        val storedOnly = timeline.actorPosts(viewer, actorUri, before = before, limit = STORED_POSTS_LIMIT_PER_PAGE)
            .filter { lowest == null || it.position.score >= lowest }
            .filterNot { it.boostedBy == null && it.noteRef in outboxNoteRefs }
            .map { it.toProfilePost(openable = true) to it.position.score }
        val posts = (outboxPosts.map { it to it.sortScore() } + storedOnly).sortedByDescending { it.second }.map { it.first }
        val nextPageId = outboxPage.nextPageId
        return ProfilePostPage(
            posts = posts,
            nextCursor = if (nextPageId == null) null else ProfilePostCursor(nextPageId = nextPageId, before = lowest),
        )
    }

    /**
     * 相手のサーバーから取った投稿を、保存済みならいいねなどが付いた保存済みの方に替え、保存していなければいいねするときに取り込めるようにする。
     * 保存済みでも、フォローしていない相手の投稿は個別投稿画面で開けない
     */
    private fun withLikes(viewer: AccountId, followed: Boolean, posts: List<ProfilePost>): List<ProfilePost> {
        val objectUris = posts.mapNotNull { (it.noteRef as? TimelineNoteRef.Remote)?.objectUri }.toSet()
        val storedByNoteRef = timeline.publishedRemotePosts(viewer, objectUris).associateBy { it.noteRef }
        return posts.map { post ->
            val stored = storedByNoteRef[post.noteRef]
            val objectUri = (post.noteRef as? TimelineNoteRef.Remote)?.objectUri
            when {
                stored != null -> stored.toProfilePost(openable = followed).copy(pinned = post.pinned)
                objectUri != null -> post.copy(like = ProfilePostLike(postId = UnsavedRemoteNoteLikeId.of(objectUri), likedByMe = false))
                else -> post
            }
        }
    }

    /**
     * 相手が並べた順で、固定した投稿を先頭に置く。同じ投稿は一覧から外す
     */
    private fun withFeaturedPosts(posts: List<ProfilePost>, featured: List<ProfilePost>): List<ProfilePost> {
        if (featured.isEmpty()) return posts
        val postsByNoteRef = posts.filter { it.boostedNoteAuthor == null }.associateBy { it.noteRef }
        val pinnedPosts = featured.map { (postsByNoteRef[it.noteRef] ?: it).copy(pinned = true) }
        val pinnedNoteRefs = pinnedPosts.map { it.noteRef }.toSet()
        return pinnedPosts + posts.filterNot { it.boostedNoteAuthor == null && it.noteRef in pinnedNoteRefs }
    }

    /**
     * 相手のサーバーが遅くてもプロフィールを待たせないよう期限を切る。
     *
     * 取得はこの呼び出しから切り離して動かす。ソケットの読み取りはスレッドの割り込みで止まらないことがあり、
     * 同じコルーチンで待つと期限を過ぎても読み取りのタイムアウトまで戻れない
     *
     * @return 取れないか、期限を過ぎたら null
     */
    private suspend fun <T> detached(timeout: Duration, label: String, actorUri: String, fetch: suspend (RemoteUserProfiles) -> T): T? {
        val profiles = remoteProfiles ?: return null
        val fetching = remoteFetchScope.async {
            try {
                fetch(profiles)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logger.warn("${label}を取れなかった: $actorUri", exception)
                null
            }
        }
        val result = try {
            withTimeoutOrNull(timeout) { fetching.await() }
        } finally {
            fetching.cancel()
        }
        if (result == null) logger.warn("${label}の取得が期限を過ぎたか、取れなかった: $actorUri")
        return result
    }

    private fun storedPosts(viewer: AccountId, actorUri: String, before: Long?): List<ProfilePost> =
        timeline.actorPosts(viewer, actorUri, before = before, limit = STORED_POSTS_LIMIT_PER_PAGE).map { it.toProfilePost(openable = true) }

    private fun ProfilePost.sortScore(): Long = Instant.parse(publishedAt).toEpochMilli()

    /**
     * @param openable 見ているアカウントが個別投稿画面で開けるか。開けなければ OGP も引かない
     */
    private fun TimelinePost.toProfilePost(openable: Boolean): ProfilePost {
        return ProfilePost(
            contentHtml = contentHtml,
            publishedAt = publishedAt.toString(),
            summary = summary,
            sensitive = sensitive,
            linkPreviewId = if (openable) position.member else null,
            postId = if (openable) position.member else null,
            deletableNoteId = localPublicId?.value,
            pinned = false,
            pinnableNoteId = null,
            images = images,
            emojis = emojis,
            favouriteCount = favouriteCount,
            stamps = stamps,
            like = ProfilePostLike(postId = position.member, likedByMe = likedByMe),
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
         * outbox の 1 ページに混ぜる保存済みの投稿の上限。範囲は outbox のページで決まるので、普通はここまで届かない
         */
        const val STORED_POSTS_LIMIT_PER_PAGE = 40

        val FEATURED_POSTS_TIMEOUT: Duration = 3.seconds

        val OUTBOX_TIMEOUT: Duration = 5.seconds

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
     * 相手の outbox の 1 ページ
     *
     * @param pageId 読むページ。null なら最初のページ
     */
    suspend fun recentPostsPage(actorUri: String, pageId: String?): RemoteProfilePostPage

    /**
     * 相手がプロフィールに固定した投稿。相手が並べた順
     */
    suspend fun featuredPosts(actorUri: String): List<ProfilePost>
}

/**
 * @param posts 新しい順
 * @param nextPageId 続きのページ。無ければ null
 */
data class RemoteProfilePostPage(
    val posts: List<ProfilePost>,
    val nextPageId: String?,
)

/**
 * @param nextCursor 続きを [UserProfileService.remotePosts] で読むときに渡す。続きが無ければ null
 */
data class ProfilePostPage(
    val posts: List<ProfilePost>,
    val nextCursor: ProfilePostCursor?,
)

/**
 * @param nextPageId 次に読む outbox のページ
 * @param before 返した範囲の下端。これより古い保存済みの投稿を次に混ぜる。範囲が決まっていなければ null
 */
@Serializable
data class ProfilePostCursor(
    @SerialName("nextPageId")
    val nextPageId: String,
    @SerialName("before")
    val before: Long?,
) {
    fun encode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(Json.encodeToString(serializer(), this).toByteArray())

    companion object {
        /**
         * 読めなければ null
         */
        fun decode(encoded: String): ProfilePostCursor? =
            runCatching {
                Json.decodeFromString(serializer(), Base64.getUrlDecoder().decode(encoded).decodeToString())
            }.getOrNull()
    }
}

sealed interface RemoteProfilePostsResult {
    data class Found(val page: ProfilePostPage) : RemoteProfilePostsResult

    data object NotLoggedIn : RemoteProfilePostsResult

    /**
     * 相手のサーバーから読めなかった
     */
    data object Unreachable : RemoteProfilePostsResult
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
    val postsNextCursor: ProfilePostCursor?,
)

/**
 * @param linkPreviewId UserInfo.linkPreviews に渡せる id。OGP を取れない投稿は null
 * @param deletableNoteId 見ているアカウントの投稿なら deleteNote に渡す id。消せない投稿は null
 * @param pinned プロフィールの先頭に固定されているか。他のサーバーの投稿は固定を受け取らないので false
 * @param pinnableNoteId 見ているアカウントのプロフィールの投稿なら setPinned に渡す id。固定できない投稿は null
 * @param images URL にはしていない画像
 * @param favouriteCount 他のサーバーから届いたお気に入りの数。他のサーバーの投稿は数を受け取らないので null
 * @param stamps 他のサーバーから届いたスタンプ。他のサーバーの投稿は受け取らないので空
 * @param like いいねできない投稿は null
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
 * @param postId いいねを付け外しするときに渡す、タイムラインの投稿の id。保存していない投稿は [UnsavedRemoteNoteLikeId] で作った id
 */
data class ProfilePostLike(
    val postId: String,
    val likedByMe: Boolean,
)
