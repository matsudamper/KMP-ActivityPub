package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.TimelineCursor
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteUrls

/**
 * 返信の投稿から、返信先の投稿を引く。返信先を順にたどれるよう、返した投稿の id もまた渡せる
 *
 * @param unsavedNotes 保存していない他のサーバーの返信先を、取ってきて保存する
 */
class ReplyTargetService(
    private val domain: String,
    private val timeline: TimelineRepository,
    private val unsavedNotes: UnsavedRemoteNotes,
) {
    /**
     * @param postId タイムラインの投稿の id
     * @return 返信でないか、返信先を読めなければ null
     */
    suspend fun replyTarget(viewer: AccountId, postId: String): TimelinePost? {
        if (!postId.matches(TimelineCursor.MEMBER_PATTERN)) return null
        val inReplyTo = timeline.inReplyTo(viewer, postId) ?: return null
        val localPublicId = localPublicIdOf(inReplyTo)
        return if (localPublicId == null) {
            remotePost(viewer, inReplyTo)
        } else {
            val member = timeline.localPosition(localPublicId)?.member ?: return null
            timeline.hydrate(viewer, listOf(member)).firstOrNull()
        }
    }

    private suspend fun remotePost(viewer: AccountId, objectUri: String): TimelinePost? {
        storedRemotePost(viewer, objectUri)?.let { return it }
        // 相手のサーバーに取りに行けるのは https の URI だけ
        if (!objectUri.startsWith("https://")) return null
        unsavedNotes.import(viewer, objectUri) ?: return null
        return storedRemotePost(viewer, objectUri)
    }

    private fun storedRemotePost(viewer: AccountId, objectUri: String): TimelinePost? =
        timeline.storedRemotePosts(viewer, setOf(objectUri))
            .firstOrNull { (it.noteRef as? TimelineNoteRef.Remote)?.objectUri == objectUri }

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
