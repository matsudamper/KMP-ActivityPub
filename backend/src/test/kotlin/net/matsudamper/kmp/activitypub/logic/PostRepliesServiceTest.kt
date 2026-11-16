package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.timeline.FakeTimelineRepository
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteUrls

class PostRepliesServiceTest {
    private val viewer = AccountId(1)
    private val blockRepositories = FakeRepositories()
    private val timeline = FakeTimelineRepository()
    private val service = PostRepliesService(
        timeline = timeline,
        noteShareUrls = NoteShareUrls(DOMAIN),
        blockLists = BlockLists(blockRepositories.serverBlockRepository, blockRepositories.actorBlockRepository),
    )

    @Test
    fun repliesToLocalPostAreFoundByItsNoteUrl() {
        timeline.positions.add(TimelinePosition(1, "l1"))
        // FakeTimelineRepository は member を公開 id にする
        val reply = remoteReply(member = "r2", host = "remote.example", inReplyTo = NoteUrls(DOMAIN, KotpubPublicNoteId("l1")).noteUrl)
        timeline.replies.add(reply)
        timeline.replies.add(remoteReply(member = "r3", host = "remote.example", inReplyTo = "https://remote.example/notes/other"))

        assertEquals(listOf(reply), service.replies(viewer, "l1"))
    }

    @Test
    fun repliesToUnsavedRemotePostAreFoundByItsObjectUri() {
        val reply = remoteReply(member = "r2", host = "remote.example", inReplyTo = "https://remote.example/notes/1")
        timeline.replies.add(reply)

        assertEquals(listOf(reply), service.replies(viewer, UnsavedRemoteNoteId.of("https://remote.example/notes/1")))
    }

    @Test
    fun repliesFromBlockedServerAreHidden() {
        timeline.positions.add(TimelinePosition(1, "l1"))
        val inReplyTo = NoteUrls(DOMAIN, KotpubPublicNoteId("l1")).noteUrl
        val visible = remoteReply(member = "r2", host = "remote.example", inReplyTo = inReplyTo)
        timeline.replies.add(remoteReply(member = "r3", host = "blocked.example", inReplyTo = inReplyTo))
        timeline.replies.add(visible)
        blockRepositories.serverBlockRepository.add(viewer, "blocked.example", Instant.EPOCH)

        assertEquals(listOf(visible), service.replies(viewer, "l1"))
    }

    @Test
    fun unreadablePostHasNoReplies() {
        timeline.replies.add(remoteReply(member = "r2", host = "remote.example", inReplyTo = NoteUrls(DOMAIN, KotpubPublicNoteId("l1")).noteUrl))

        assertEquals(listOf(), service.replies(viewer, "l1"))
        assertEquals(listOf(), service.replies(viewer, "不正な id"))
    }

    private fun remoteReply(member: String, host: String, inReplyTo: String): TimelinePost = TimelinePost(
        position = TimelinePosition(1, member),
        contentHtml = "<p>返信</p>",
        publishedAt = Instant.EPOCH,
        actorUri = "https://$host/users/$member",
        username = member,
        displayName = null,
        summary = null,
        sensitive = false,
        actorIconUrl = null,
        actorUpdatedAt = null,
        images = listOf(),
        emojis = listOf(),
        actorEmojis = listOf(),
        favouriteCount = null,
        stamps = listOf(),
        likedByMe = false,
        boostedByMe = false,
        reactionMember = member,
        noteRef = TimelineNoteRef.Remote("https://$host/notes/$member"),
        boostCount = null,
        boostedBy = null,
        deleted = false,
        boostUndone = false,
        inReplyTo = inReplyTo,
        quoteUri = null,
        editedAt = null,
    )

    private companion object {
        const val DOMAIN = "local.example"
    }
}
