package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.TimelineCursor
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteUrls

/**
 * 返信の投稿から、返信先の投稿を引く。返信先を順にたどれるよう、返した投稿の id もまた渡せる。
 * 保存していない他のサーバーの返信先は、取ってきても保存しない。id は [UnsavedRemoteNoteId] にして、いいねやブーストを付けたときに保存させる
 */
class ReplyTargetService(
    private val domain: String,
    private val timeline: TimelineRepository,
    private val unsavedNotes: UnsavedRemoteNotes,
) {
    /**
     * @param postId タイムラインの投稿の id か、[UnsavedRemoteNoteId] の id
     */
    suspend fun replyTarget(viewer: AccountId, postId: String): ReplyTargetResult {
        val inReplyTo = when (val lookup = inReplyToOf(viewer, postId)) {
            is InReplyToLookup.Found -> lookup.objectUri
            InReplyToLookup.NotFound -> return ReplyTargetResult.NotFound
            InReplyToLookup.Unavailable -> return ReplyTargetResult.Unavailable
        }
        val localPublicId = localPublicIdOf(inReplyTo)
        return if (localPublicId == null) {
            remotePost(viewer, inReplyTo)
        } else {
            timeline.localPost(viewer, localPublicId)?.let { ReplyTargetResult.Found(it) } ?: ReplyTargetResult.NotFound
        }
    }

    private suspend fun inReplyToOf(viewer: AccountId, postId: String): InReplyToLookup {
        val unsavedObjectUri = UnsavedRemoteNoteId.objectUriOf(postId)
        if (unsavedObjectUri == null) {
            if (!postId.matches(TimelineCursor.MEMBER_PATTERN)) return InReplyToLookup.NotFound
            return InReplyToLookup.of(timeline.inReplyTo(viewer, postId))
        }
        // 表示した後に、いいねやブーストで保存されていることがある
        val stored = storedRemotePost(viewer, unsavedObjectUri)
        if (stored != null) return InReplyToLookup.of(stored.inReplyTo)
        return when (val fetched = unsavedNotes.fetch(unsavedObjectUri)) {
            is RemoteNoteFetchResult.Found -> InReplyToLookup.of(fetched.note.inReplyTo)
            RemoteNoteFetchResult.NotFound -> InReplyToLookup.NotFound
            RemoteNoteFetchResult.Unavailable -> InReplyToLookup.Unavailable
        }
    }

    private suspend fun remotePost(viewer: AccountId, objectUri: String): ReplyTargetResult {
        val stored = storedRemotePost(viewer, objectUri)
        if (stored != null) return ReplyTargetResult.Found(stored)
        // 相手のサーバーに取りに行けるのは https の URI だけ
        if (!objectUri.startsWith("https://")) return ReplyTargetResult.NotFound
        return when (val fetched = unsavedNotes.fetch(objectUri)) {
            is RemoteNoteFetchResult.Found -> ReplyTargetResult.Found(fetched.toUnsavedPost())
            RemoteNoteFetchResult.NotFound -> ReplyTargetResult.NotFound
            RemoteNoteFetchResult.Unavailable -> ReplyTargetResult.Unavailable
        }
    }

    private fun storedRemotePost(viewer: AccountId, objectUri: String): TimelinePost? =
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
        )
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

    private sealed interface InReplyToLookup {
        data class Found(val objectUri: String) : InReplyToLookup

        /** 返信でないか、読めない投稿 */
        data object NotFound : InReplyToLookup

        data object Unavailable : InReplyToLookup

        companion object {
            fun of(inReplyTo: String?): InReplyToLookup = if (inReplyTo == null) NotFound else Found(inReplyTo)
        }
    }
}

sealed interface ReplyTargetResult {
    data class Found(val post: TimelinePost) : ReplyTargetResult

    /**
     * 返信でないか、返信先が無いか読めない
     */
    data object NotFound : ReplyTargetResult

    /**
     * 返信先のサーバーが応えなかった。時間を置けば取れることがある
     */
    data object Unavailable : ReplyTargetResult
}
