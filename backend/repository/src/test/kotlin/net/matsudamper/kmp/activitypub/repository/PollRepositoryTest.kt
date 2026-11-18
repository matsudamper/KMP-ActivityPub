package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class PollRepositoryTest {
    @Test
    fun singleChoiceVotesAreCountedOncePerVoter() = withRepository { repositories ->
        repositories.deliveryQueueRepository.enqueueNote(notePost(multipleChoice = false))
        val polls = repositories.pollRepository

        assertTrue(polls.recordVote(vote(actor = "$ACTOR/a", choice = "犬")))
        assertFalse(polls.recordVote(vote(actor = "$ACTOR/a", choice = "猫")))
        assertTrue(polls.recordVote(vote(actor = "$ACTOR/b", choice = "猫")))
        assertFalse(polls.recordVote(vote(actor = "$ACTOR/c", choice = "鳥")))
        assertFalse(polls.recordVote(vote(actor = "$ACTOR/d", choice = "犬").copy(votedAt = ENDS_AT)))

        assertEquals(
            mapOf(
                PUBLIC_ID to PollRecord(
                    options = listOf(PollOptionRecord(name = "犬", votesCount = 1), PollOptionRecord(name = "猫", votesCount = 1)),
                    multipleChoice = false,
                    endsAt = ENDS_AT,
                    votersCount = 2,
                ),
            ),
            polls.localPolls(setOf(PUBLIC_ID, PublicNoteId("other"))),
        )
    }

    @Test
    fun multipleChoiceVotesAreCountedPerOption() = withRepository { repositories ->
        repositories.deliveryQueueRepository.enqueueNote(notePost(multipleChoice = true))
        val polls = repositories.pollRepository

        assertTrue(polls.recordVote(vote(actor = "$ACTOR/a", choice = "犬")))
        assertTrue(polls.recordVote(vote(actor = "$ACTOR/a", choice = "猫")))
        assertFalse(polls.recordVote(vote(actor = "$ACTOR/a", choice = "猫")))

        val poll = polls.localPolls(setOf(PUBLIC_ID)).getValue(PUBLIC_ID)
        assertEquals(listOf(1L, 1L), poll.options.map { it.votesCount })
        assertEquals(1L, poll.votersCount)
    }

    @Test
    fun remotePollIsReplacedByUpdate() = withRepository { repositories ->
        val objectUri = "$ACTOR/notes/1"
        val first = PollRecord(
            options = listOf(PollOptionRecord(name = "犬", votesCount = 0), PollOptionRecord(name = "猫", votesCount = null)),
            multipleChoice = false,
            endsAt = ENDS_AT,
            votersCount = 0,
        )
        repositories.timelineRepository.receive(remoteNote(objectUri = objectUri, poll = first))
        assertEquals(mapOf(objectUri to first), repositories.pollRepository.remotePolls(setOf(objectUri)))

        val counted = first.copy(options = listOf(PollOptionRecord(name = "犬", votesCount = 3), PollOptionRecord(name = "猫", votesCount = 1)), votersCount = 4)
        repositories.timelineRepository.reviseRemote(remoteNote(objectUri = objectUri, poll = counted))
        assertEquals(mapOf(objectUri to counted), repositories.pollRepository.remotePolls(setOf(objectUri)))
    }

    private fun notePost(multipleChoice: Boolean): NotePost = NotePost(
        username = "alice",
        requestId = "request",
        publicId = PUBLIC_ID,
        contentHtml = "<p>どっち</p>",
        summary = null,
        sourceText = "どっち",
        publishedAt = NOW,
        images = listOf(),
        body = "create",
        inboxes = listOf(),
        inReplyTo = null,
        quoteUri = null,
        quoteInbox = null,
        mentions = listOf(),
        poll = NotePollInput(options = listOf("犬", "猫"), multipleChoice = multipleChoice, endsAt = ENDS_AT),
    )

    private fun vote(actor: String, choice: String): PollVoteInput = PollVoteInput(
        publicId = PUBLIC_ID,
        actorUri = actor,
        voteUri = "$actor/votes/$choice",
        choice = choice,
        votedAt = NOW,
    )

    private fun remoteNote(objectUri: String, poll: PollRecord): RemoteNoteInput = RemoteNoteInput(
        objectUri = objectUri, actorUri = ACTOR, contentHtml = "<p>どっち</p>", language = null, visibility = "public",
        localRecipients = setOf("alice"), inReplyTo = null, quoteUri = null, summary = null, sensitive = false, publishedAt = NOW, receivedAt = NOW, editedAt = null,
        images = listOf(),
        emojis = listOf(),
        poll = poll,
    )

    private fun withRepository(block: (Repositories) -> Unit) {
        val directory = createTempDirectory("poll-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            Accounts.insert(path, "alice", "hash", "type", "public-key", "private-key", NOW.toString())
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                repositories.remoteActorRepository.save(
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null, null, listOf(), null, listOf())),
                    NOW,
                )
                block(repositories)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val ACTOR = "https://remote.example/users/bob"
        val PUBLIC_ID = PublicNoteId("poll")
        val NOW: Instant = Instant.parse("2026-01-01T00:00:00Z")
        val ENDS_AT: Instant = Instant.parse("2026-01-02T00:00:00Z")
    }
}
