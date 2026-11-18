package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class NoteReplyRepositoryTest {
    @Test
    fun readableRemotePostIsRepliedToItsAuthor() = withRepository { repositories, alice ->
        repositories.followingRepository.add(alice, ACTOR, NOW)
        val member = assertNotNull(repositories.timelineRepository.receive(note("1", "public", setOf()))).member

        val target = assertIs<ReplyTarget.Remote>(repositories.noteRepository.replyTarget(alice, member))
        assertEquals("$ACTOR/notes/1", target.objectUri)
        assertEquals(ACTOR, target.actorUri)
        assertEquals("remote", target.preferredUsername)
        assertEquals("$ACTOR/inbox", target.inbox)
    }

    @Test
    fun unreadableOrMissingPostCannotBeRepliedTo() = withRepository { repositories, alice ->
        val directOnly = assertNotNull(repositories.timelineRepository.receive(note("direct", "direct", setOf("bob")))).member

        assertNull(repositories.noteRepository.replyTarget(alice, directOnly))
        assertNull(repositories.noteRepository.replyTarget(alice, "l999"))
    }

    @Test
    fun nonPublicPostCannotBeRepliedToEvenIfReadable() = withRepository { repositories, alice ->
        repositories.followingRepository.add(alice, ACTOR, NOW)
        val followersOnly = assertNotNull(repositories.timelineRepository.receive(note("followers", "followers", setOf("alice")))).member
        val direct = assertNotNull(repositories.timelineRepository.receive(note("direct", "direct", setOf("alice")))).member

        assertNull(repositories.noteRepository.replyTarget(alice, followersOnly))
        assertNull(repositories.noteRepository.replyTarget(alice, direct))
    }

    @Test
    fun localPostIsRepliedWithoutInbox() = withRepository { repositories, alice ->
        repositories.deliveryQueueRepository.enqueueNote(notePost(username = "bob", publicId = "bob-note"))
        val member = checkNotNull(repositories.timelineRepository.localPosition("bob-note")).member

        assertEquals(ReplyTarget.Local(username = "bob", publicId = PublicNoteId("bob-note")), repositories.noteRepository.replyTarget(alice, member))
    }

    @Test
    fun replyIsRecordedWithItsTargetAndMentionsInOrder() = withRepository { repositories, _ ->
        val mentions = listOf(
            NoteMention(actorUri = ACTOR, acct = "remote@remote.example", inbox = "$ACTOR/inbox"),
            NoteMention(actorUri = "https://local.example/users/bob", acct = "bob@local.example", inbox = null),
        )
        repositories.deliveryQueueRepository.enqueueNote(
            notePost(username = "alice", publicId = "reply").copy(inReplyTo = "$ACTOR/notes/1", mentions = mentions),
        )

        val note = assertNotNull(repositories.noteRepository.find(PublicNoteId("reply")))
        assertEquals("$ACTOR/notes/1", note.inReplyTo)
        assertEquals(mentions, note.mentions)
        assertEquals(mentions, repositories.noteRepository.list("alice", after = null, limit = 10).single().mentions)
    }

    @Test
    fun quoteIsRecordedOnLocalAndRemotePosts() = withRepository { repositories, alice ->
        repositories.followingRepository.add(alice, ACTOR, NOW)
        repositories.deliveryQueueRepository.enqueueNote(notePost(username = "alice", publicId = "quote").copy(quoteUri = "$ACTOR/notes/1", quoteInbox = "$ACTOR/inbox"))
        val remote = assertNotNull(repositories.timelineRepository.receive(note("2", "public", setOf()).copy(quoteUri = "https://other.example/notes/3"))).member
        val local = checkNotNull(repositories.timelineRepository.localPosition("quote")).member

        val quote = assertNotNull(repositories.noteRepository.find(PublicNoteId("quote")))
        assertEquals("$ACTOR/notes/1", quote.quoteUri)
        assertEquals("$ACTOR/inbox", quote.quoteInbox)
        assertEquals("$ACTOR/notes/1", assertNotNull(repositories.timelineRepository.viewablePost(alice, local)).quoteUri)
        assertEquals("https://other.example/notes/3", assertNotNull(repositories.timelineRepository.viewablePost(alice, remote)).quoteUri)
    }

    @Test
    fun repliesFromLocalAndRemotePostsAreListedOldestFirst() = withRepository { repositories, alice ->
        val target = "$ACTOR/notes/1"
        repositories.timelineRepository.receive(note("late", "public", setOf("alice")).copy(inReplyTo = target, publishedAt = NOW.plusSeconds(20)))
        repositories.deliveryQueueRepository.enqueueNote(
            notePost(username = "bob", publicId = "bob-reply").copy(inReplyTo = target, publishedAt = NOW.plusSeconds(10)),
        )
        repositories.timelineRepository.receive(note("early", "public", setOf("alice")).copy(inReplyTo = target))
        repositories.timelineRepository.receive(note("other", "public", setOf("alice")).copy(inReplyTo = "$ACTOR/notes/2"))

        val replies = repositories.timelineRepository.replies(alice, target, limit = 10) { false }

        assertEquals(
            listOf(TimelineNoteRef.Remote("$ACTOR/notes/early"), TimelineNoteRef.Local(PublicNoteId("bob-reply")), TimelineNoteRef.Remote("$ACTOR/notes/late")),
            replies.map { it.noteRef },
        )
        assertEquals(listOf(TimelineNoteRef.Remote("$ACTOR/notes/early")), repositories.timelineRepository.replies(alice, target, limit = 1) { false }.map { it.noteRef })
    }

    @Test
    fun repliesFromHiddenAuthorsDoNotCountTowardLimit() = withRepository { repositories, alice ->
        val target = "$ACTOR/notes/1"
        val hiddenActor = "https://hidden.example/users/hidden"
        repositories.remoteActorRepository.save(
            RemoteActorRecord(hiddenActor, "$hiddenActor/inbox", null, "key", RemoteActorProfileRecord("hidden", "Hidden", null, null, null, listOf(), null, listOf())),
            NOW,
        )
        repositories.timelineRepository.receive(
            note("unused", "public", setOf("alice")).copy(objectUri = "$hiddenActor/notes/1", actorUri = hiddenActor, inReplyTo = target),
        )
        repositories.timelineRepository.receive(note("visible", "public", setOf("alice")).copy(inReplyTo = target, publishedAt = NOW.plusSeconds(10)))

        val replies = repositories.timelineRepository.replies(alice, target, limit = 1) { actorUri -> actorUri == hiddenActor }

        assertEquals(listOf(TimelineNoteRef.Remote("$ACTOR/notes/visible")), replies.map { it.noteRef })
    }

    @Test
    fun unreadableOrDeletedRepliesAreNotListed() = withRepository { repositories, alice ->
        val target = "$ACTOR/notes/1"
        repositories.timelineRepository.receive(note("direct", "direct", setOf("bob")).copy(inReplyTo = target))
        repositories.timelineRepository.receive(note("deleted", "public", setOf("alice")).copy(inReplyTo = target))
        repositories.timelineRepository.deleteRemote(ACTOR, "$ACTOR/notes/deleted", NOW)

        assertEquals(listOf(), repositories.timelineRepository.replies(alice, target, limit = 10) { false })
    }

    private fun notePost(username: String, publicId: String): NotePost = NotePost(
        username = username,
        requestId = "request-$publicId",
        publicId = PublicNoteId(publicId),
        contentHtml = "<p>本文</p>",
        summary = null,
        sourceText = "",
        publishedAt = NOW,
        images = listOf(),
        body = "create",
        inboxes = listOf(),
        inReplyTo = null,
        quoteUri = null,
        quoteInbox = null,
        mentions = listOf(),
        sensitive = false,
        poll = null,
    )

    private fun note(id: String, visibility: String, recipients: Set<String>): RemoteNoteInput = RemoteNoteInput(
        objectUri = "$ACTOR/notes/$id", actorUri = ACTOR, contentHtml = "<p>本文</p>", language = null, visibility = visibility,
        localRecipients = recipients, inReplyTo = null, quoteUri = null, summary = null, sensitive = false, publishedAt = NOW, receivedAt = NOW, editedAt = null,
        images = listOf(),
        emojis = listOf(),
        poll = null,
    )

    private fun withRepository(block: (Repositories, AccountId) -> Unit) {
        val directory = createTempDirectory("note-reply-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(path, username, "hash", "type", "public-key", "private-key", NOW.toString())
            }
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                repositories.remoteActorRepository.save(
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null, null, listOf(), null, listOf())),
                    NOW,
                )
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                block(repositories, alice)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val ACTOR = "https://remote.example/actor"
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
