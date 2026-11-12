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

    private fun notePost(username: String, publicId: String): NotePost = NotePost(
        username = username,
        requestId = "request-$publicId",
        publicId = PublicNoteId(publicId),
        contentHtml = "<p>本文</p>",
        publishedAt = NOW,
        images = listOf(),
        body = "create",
        inboxes = listOf(),
        inReplyTo = null,
        mentions = listOf(),
    )

    private fun note(id: String, visibility: String, recipients: Set<String>): RemoteNoteInput = RemoteNoteInput(
        objectUri = "$ACTOR/notes/$id", actorUri = ACTOR, contentHtml = "<p>本文</p>", visibility = visibility,
        localRecipients = recipients, inReplyTo = null, summary = null, sensitive = false, publishedAt = NOW, receivedAt = NOW,
        images = listOf(),
        emojis = listOf(),
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
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null, null, listOf())),
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
