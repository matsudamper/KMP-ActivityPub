package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class PostSearchRepositoryTest {
    @Test
    fun matchingLocalAndRemotePostsAreListedNewestFirst() = withRepository { repositories, alice ->
        repositories.followingRepository.add(alice, ACTOR, NOW)
        repositories.timelineRepository.receive(note("old", "<p>りんご</p>").copy(publishedAt = NOW))
        repositories.deliveryQueueRepository.enqueueNote(notePost(username = "bob", publicId = "middle", contentHtml = "<p>りんご</p>").copy(publishedAt = NOW.plusSeconds(10)))
        repositories.timelineRepository.receive(note("new", "<p>りんご</p>").copy(publishedAt = NOW.plusSeconds(20)))
        repositories.timelineRepository.receive(note("other", "<p>みかん</p>").copy(publishedAt = NOW.plusSeconds(30)))

        val found = repositories.timelineRepository.search(alice, before = null, limit = 10)

        assertEquals(
            listOf(TimelineNoteRef.Remote("$ACTOR/notes/new"), TimelineNoteRef.Local(PublicNoteId("middle")), TimelineNoteRef.Remote("$ACTOR/notes/old")),
            found.map { it.noteRef },
        )
    }

    @Test
    fun continuationStartsAfterTheLastReturnedPost() = withRepository { repositories, alice ->
        repositories.followingRepository.add(alice, ACTOR, NOW)
        // 同じ時刻の投稿も取りこぼさず、二度返さない
        listOf("1", "2", "3").forEach { id -> repositories.timelineRepository.receive(note(id, "<p>りんご</p>")) }
        repositories.deliveryQueueRepository.enqueueNote(notePost(username = "bob", publicId = "local", contentHtml = "<p>りんご</p>"))

        val first = repositories.timelineRepository.search(alice, before = null, limit = 3)
        val last = first.last()
        val second = repositories.timelineRepository.search(alice, before = PublishedNotePosition(last.publishedAt, last.position.member), limit = 3)

        assertEquals(3, first.size)
        assertEquals(1, second.size)
        assertEquals(4, (first + second).map { it.noteRef }.toSet().size)
    }

    @Test
    fun unmatchedPostsDoNotCountTowardLimit() = withRepository { repositories, alice ->
        repositories.followingRepository.add(alice, ACTOR, NOW)
        repositories.timelineRepository.receive(note("match", "<p>りんご</p>"))
        repositories.timelineRepository.receive(note("newer", "<p>みかん</p>").copy(publishedAt = NOW.plusSeconds(10)))

        val found = repositories.timelineRepository.search(alice, before = null, limit = 1)

        assertEquals(listOf(TimelineNoteRef.Remote("$ACTOR/notes/match")), found.map { it.noteRef })
    }

    @Test
    fun unfollowedAuthorsPostsAreFoundOnlyWhenPublic() = withRepository { repositories, alice ->
        val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
        repositories.followingRepository.add(bob, ACTOR, NOW)
        repositories.timelineRepository.receive(note("public", "<p>りんご</p>"))
        repositories.timelineRepository.receive(note("unlisted", "<p>りんご</p>").copy(visibility = "unlisted"))
        repositories.timelineRepository.receive(note("direct", "<p>りんご</p>").copy(visibility = "direct", localRecipients = setOf("bob")))

        assertEquals(
            listOf(TimelineNoteRef.Remote("$ACTOR/notes/public")),
            repositories.timelineRepository.search(alice, before = null, limit = 10).map { it.noteRef },
        )
        assertEquals(
            setOf("public", "unlisted", "direct").map { TimelineNoteRef.Remote("$ACTOR/notes/$it") }.toSet(),
            repositories.timelineRepository.search(bob, before = null, limit = 10).map { it.noteRef }.toSet(),
        )
    }

    @Test
    fun deletedPostsAndHiddenAuthorsAreNotFound() = withRepository { repositories, alice ->
        val hiddenActor = "https://hidden.example/users/hidden"
        repositories.remoteActorRepository.save(
            RemoteActorRecord(hiddenActor, "$hiddenActor/inbox", null, "key", RemoteActorProfileRecord("hidden", "Hidden", null, null, null, listOf(), null, listOf())),
            NOW,
        )
        repositories.followingRepository.add(alice, ACTOR, NOW)
        repositories.followingRepository.add(alice, hiddenActor, NOW)
        repositories.timelineRepository.receive(note("deleted", "<p>りんご</p>"))
        repositories.timelineRepository.deleteRemote(ACTOR, "$ACTOR/notes/deleted", NOW)
        repositories.timelineRepository.receive(note("hidden", "<p>りんご</p>").copy(objectUri = "$hiddenActor/notes/1", actorUri = hiddenActor))

        val found = repositories.timelineRepository.searchPosts(
            viewer = alice,
            before = null,
            limit = 10,
            hidesAuthor = { actorUri -> actorUri == hiddenActor },
            matchesContent = { it.contains(KEYWORD) },
        )

        assertEquals(listOf(), found)
    }

    private fun TimelineRepository.search(viewer: AccountId, before: PublishedNotePosition?, limit: Int): List<TimelinePost> =
        searchPosts(viewer = viewer, before = before, limit = limit, hidesAuthor = { false }, matchesContent = { it.contains(KEYWORD) })

    private fun notePost(username: String, publicId: String, contentHtml: String): NotePost = NotePost(
        username = username,
        requestId = "request-$publicId",
        publicId = PublicNoteId(publicId),
        contentHtml = contentHtml,
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

    private fun note(id: String, contentHtml: String): RemoteNoteInput = RemoteNoteInput(
        objectUri = "$ACTOR/notes/$id", actorUri = ACTOR, contentHtml = contentHtml, language = null, visibility = "public",
        localRecipients = setOf(), inReplyTo = null, quoteUri = null, summary = null, sensitive = false, publishedAt = NOW, receivedAt = NOW, editedAt = null,
        images = listOf(),
        emojis = listOf(),
        linkHref = null,
        poll = null,
    )

    private fun withRepository(block: (Repositories, AccountId) -> Unit) {
        val directory = createTempDirectory("post-search-test")
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
        const val KEYWORD = "りんご"
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
