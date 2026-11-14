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
     * @return 返信でないか、返信先を読めなければ null
     */
    suspend fun replyTarget(viewer: AccountId, postId: String): TimelinePost? {
        val inReplyTo = inReplyToOf(viewer, postId) ?: return null
        val localPublicId = localPublicIdOf(inReplyTo)
        return if (localPublicId == null) {
            remotePost(viewer, inReplyTo)
        } else {
            val member = timeline.localPosition(localPublicId)?.member ?: return null
            timeline.hydrate(viewer, listOf(member)).firstOrNull()
        }
    }

    private suspend fun inReplyToOf(viewer: AccountId, postId: String): String? {
        val unsavedObjectUri = UnsavedRemoteNoteId.objectUriOf(postId)
        if (unsavedObjectUri == null) {
            if (!postId.matches(TimelineCursor.MEMBER_PATTERN)) return null
            return timeline.inReplyTo(viewer, postId)
        }
        // 表示した後に、いいねやブーストで保存されていることがある
        val stored = storedRemotePost(viewer, unsavedObjectUri)
        if (stored != null) return stored.inReplyTo
        return unsavedNotes.fetch(unsavedObjectUri)?.note?.inReplyTo
    }

    private suspend fun remotePost(viewer: AccountId, objectUri: String): TimelinePost? {
        val stored = storedRemotePost(viewer, objectUri)
        if (stored != null) return stored
        // 相手のサーバーに取りに行けるのは https の URI だけ
        if (!objectUri.startsWith("https://")) return null
        return unsavedNotes.fetch(objectUri)?.toUnsavedPost()
    }

    private fun storedRemotePost(viewer: AccountId, objectUri: String): TimelinePost? =
        timeline.storedRemotePosts(viewer, setOf(objectUri))
            .firstOrNull { (it.noteRef as? TimelineNoteRef.Remote)?.objectUri == objectUri }

    /**
     * 保存していないので、いいねもブーストも付いていない
     */
    private fun FetchedRemoteNote.toUnsavedPost(): TimelinePost {
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
}
