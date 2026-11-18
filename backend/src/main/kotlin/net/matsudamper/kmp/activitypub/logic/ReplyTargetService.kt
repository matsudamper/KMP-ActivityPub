package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.TimelineCursor
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteUrls

/**
 * 返信や引用の投稿から、返信先や引用先の投稿を引く。返信先を順にたどれるよう、返した投稿の id もまた渡せる。
 * 検索に入力された投稿の URL からも同じく引く。
 * 保存していない他のサーバーの投稿は、取ってきても保存しない。id は [UnsavedRemoteNoteId] にして、いいねやブーストを付けたときに保存させる
 */
class ReplyTargetService(
    private val domain: String,
    private val timeline: TimelineRepository,
    private val unsavedNotes: UnsavedRemoteNotes,
) {
    /**
     * @param viewer ログインしていなければ null。保存していない他のサーバーの投稿は取りに行かず、無いものとして扱う
     * @param postId タイムラインの投稿の id か、[UnsavedRemoteNoteId] の id
     */
    suspend fun replyTarget(viewer: AccountId?, postId: String): ReplyTargetResult =
        linkedPost(viewer = viewer, postId = postId, link = PostLink.Reply)

    /**
     * @param postId タイムラインの投稿の id か、[UnsavedRemoteNoteId] の id
     */
    suspend fun quoteTarget(viewer: AccountId?, postId: String): ReplyTargetResult =
        linkedPost(viewer = viewer, postId = postId, link = PostLink.Quote)

    /**
     * このサーバーの投稿は、ActivityPub の投稿の URL と画面の投稿の URL（`/posts/{id}`）を読む。
     * 他のサーバーの投稿は、画面の投稿の URL のように投稿の id と違う URL も読む
     *
     * @param url 人が入力した投稿の URL
     */
    suspend fun postByUrl(viewer: AccountId, url: String): ReplyTargetResult {
        val localPost = localPostOfUrl(viewer, url)
        if (localPost != null) return localPost
        // 相手のサーバーに取りに行けるのは https の URI だけ
        if (!url.startsWith("https://")) return ReplyTargetResult.NotFound
        val stored = storedRemotePost(viewer, url)
        if (stored != null) return ReplyTargetResult.Found(stored)
        return when (val fetched = unsavedNotes.fetchByUrl(url)) {
            // 画面の URL から引いたときは、投稿の id で保存していることがある
            is RemoteNoteFetchResult.Found -> ReplyTargetResult.Found(storedRemotePost(viewer, fetched.note.objectUri) ?: fetched.toUnsavedPost())

            RemoteNoteFetchResult.NotFound -> ReplyTargetResult.NotFound

            RemoteNoteFetchResult.Unavailable -> ReplyTargetResult.Unavailable
        }
    }

    private suspend fun linkedPost(viewer: AccountId?, postId: String, link: PostLink): ReplyTargetResult {
        val linkedUri = when (val lookup = linkedUriOf(viewer, postId, link)) {
            is LinkLookup.Found -> lookup.objectUri
            LinkLookup.NotFound -> return ReplyTargetResult.NotFound
            LinkLookup.Unavailable -> return ReplyTargetResult.Unavailable
        }
        val localPublicId = localPublicIdOf(linkedUri)
        return if (localPublicId == null) {
            remotePost(viewer, linkedUri)
        } else {
            val member = timeline.localPosition(localPublicId)?.member ?: return ReplyTargetResult.NotFound
            timeline.viewablePost(viewer, member)?.let { ReplyTargetResult.Found(it) } ?: ReplyTargetResult.NotFound
        }
    }

    private suspend fun linkedUriOf(viewer: AccountId?, postId: String, link: PostLink): LinkLookup {
        val unsavedObjectUri = UnsavedRemoteNoteId.objectUriOf(postId)
        if (unsavedObjectUri == null) {
            if (!postId.matches(TimelineCursor.MEMBER_PATTERN)) return LinkLookup.NotFound
            return LinkLookup.of(timeline.viewablePost(viewer, postId)?.let(link::of))
        }
        if (viewer == null) return LinkLookup.NotFound
        // 表示した後に、いいねやブーストで保存されていることがある
        val stored = storedRemotePost(viewer, unsavedObjectUri)
        if (stored != null) return LinkLookup.of(link.of(stored))
        return when (val fetched = unsavedNotes.fetch(unsavedObjectUri)) {
            is RemoteNoteFetchResult.Found -> LinkLookup.of(link.of(fetched.note))
            RemoteNoteFetchResult.NotFound -> LinkLookup.NotFound
            RemoteNoteFetchResult.Unavailable -> LinkLookup.Unavailable
        }
    }

    private suspend fun remotePost(viewer: AccountId?, objectUri: String): ReplyTargetResult {
        val stored = storedRemotePost(viewer, objectUri)
        if (stored != null) return ReplyTargetResult.Found(stored)
        // ログインしていない人に、相手のサーバーへ取りに行かせない
        if (viewer == null) return ReplyTargetResult.NotFound
        // 相手のサーバーに取りに行けるのは https の URI だけ
        if (!objectUri.startsWith("https://")) return ReplyTargetResult.NotFound
        return when (val fetched = unsavedNotes.fetch(objectUri)) {
            is RemoteNoteFetchResult.Found -> ReplyTargetResult.Found(fetched.toUnsavedPost())
            RemoteNoteFetchResult.NotFound -> ReplyTargetResult.NotFound
            RemoteNoteFetchResult.Unavailable -> ReplyTargetResult.Unavailable
        }
    }

    private fun storedRemotePost(viewer: AccountId?, objectUri: String): TimelinePost? =
        timeline.storedRemotePosts(viewer, setOf(objectUri))
            .firstOrNull { (it.noteRef as? TimelineNoteRef.Remote)?.objectUri == objectUri }

    /**
     * 保存していないので、いいねもブーストも付いていない
     */
    private fun RemoteNoteFetchResult.Found.toUnsavedPost(): TimelinePost {
        val unsavedId = UnsavedRemoteNoteId.of(note.objectUri)
        return TimelinePost(
            position = TimelinePosition(note.publishedAt.toEpochMilli(), unsavedId),
            contentHtml = note.contentHtml,
            language = note.language,
            publishedAt = note.publishedAt,
            actorUri = author.actorUri,
            username = author.username,
            displayName = author.displayName,
            summary = note.summary,
            sensitive = note.sensitive,
            actorIconUrl = author.iconUrl,
            actorUpdatedAt = null,
            images = note.images,
            emojis = note.emojis,
            actorEmojis = author.displayNameEmojis,
            favouriteCount = null,
            stamps = listOf(),
            likedByMe = false,
            // ブーストできるのは公開の投稿だけ
            boostedByMe = if (note.visibility == "public") false else null,
            reactionMember = unsavedId,
            noteRef = TimelineNoteRef.Remote(note.objectUri),
            boostCount = null,
            boostedBy = null,
            deleted = false,
            boostUndone = false,
            inReplyTo = note.inReplyTo,
            quoteUri = note.quoteUri,
            editedAt = null,
            linkHref = note.linkHref,
        )
    }

    /**
     * @return このサーバーの URL でなければ null
     */
    private fun localPostOfUrl(viewer: AccountId, url: String): ReplyTargetResult? {
        val localPublicId = localPublicIdOf(url)
        if (localPublicId != null) {
            val member = timeline.localPosition(localPublicId)?.member ?: return ReplyTargetResult.NotFound
            return timeline.viewablePost(viewer, member)?.let { ReplyTargetResult.Found(it) } ?: ReplyTargetResult.NotFound
        }
        val parsed = runCatching { URI(url) }.getOrNull() ?: return null
        if (parsed.host != domain) return null
        val member = localPostPathPattern.matchEntire(parsed.path.orEmpty())?.groupValues?.get(1) ?: return ReplyTargetResult.NotFound
        if (!member.matches(TimelineCursor.MEMBER_PATTERN)) return ReplyTargetResult.NotFound
        return timeline.viewablePost(viewer, member)?.let { ReplyTargetResult.Found(it) } ?: ReplyTargetResult.NotFound
    }

    /**
     * @return このサーバーの投稿の URL でなければ null
     */
    private fun localPublicIdOf(uri: String): String? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        if (parsed.host != domain) return null
        val publicId = parsed.path?.substringAfterLast('/')?.takeIf { it.isNotEmpty() } ?: return null
        return publicId.takeIf { runCatching { NoteUrls(domain, KotpubPublicNoteId(it)).noteUrl }.getOrNull() == uri }
    }

    private companion object {
        val localPostPathPattern = Regex("/posts/([^/]+)/?")
    }

    private enum class PostLink {
        Reply,
        Quote,
        ;

        fun of(post: TimelinePost): String? = when (this) {
            Reply -> post.inReplyTo
            Quote -> post.quoteUri
        }

        fun of(note: RemoteNoteInput): String? = when (this) {
            Reply -> note.inReplyTo
            Quote -> note.quoteUri
        }
    }

    private sealed interface LinkLookup {
        data class Found(val objectUri: String) : LinkLookup

        /** 返信や引用でないか、読めない投稿 */
        data object NotFound : LinkLookup

        data object Unavailable : LinkLookup

        companion object {
            fun of(linkedUri: String?): LinkLookup = if (linkedUri == null) NotFound else Found(linkedUri)
        }
    }
}

sealed interface ReplyTargetResult {
    data class Found(val post: TimelinePost) : ReplyTargetResult

    /**
     * 返信や引用でないか、返信先や引用先が無いか読めない
     */
    data object NotFound : ReplyTargetResult

    /**
     * 返信先や引用先のサーバーが応えなかった。時間を置けば取れることがある
     */
    data object Unavailable : ReplyTargetResult
}
