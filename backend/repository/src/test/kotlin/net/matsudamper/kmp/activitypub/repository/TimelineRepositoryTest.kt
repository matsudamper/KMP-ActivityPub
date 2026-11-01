package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class TimelineRepositoryTest {
    @Test
    fun onlyFollowedOrAddressedNotesAreSavedOnceAndDeletedByTheirAuthor() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        assertNull(repository.receive(note("1", "public", setOf())))
        repositories.followingRepository.add(accounts.getValue("alice"), ACTOR, NOW)
        val saved = assertNotNull(repository.receive(note("1", "public", setOf())))
        assertNull(repository.receive(note("1", "public", setOf())))
        assertEquals(listOf(saved), repository.page(accounts.getValue("alice"), null, 10))
        assertTrue(repository.page(accounts.getValue("bob"), null, 10).isEmpty())
        assertNotNull(repository.receive(note("2", "direct", setOf("bob"))))
        repository.deleteRemote("https://other.example/actor", "$ACTOR/notes/1")
        assertEquals(1, repository.page(accounts.getValue("alice"), null, 10).size)
        repository.deleteRemote(ACTOR, "$ACTOR/notes/1")
        assertTrue(repository.hydrate(accounts.getValue("alice"), listOf(saved.member)).isEmpty())
    }

    @Test
    fun fanoutUsesRecentFollowersAndExplicitRecipientsWithPrivateVisibility() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        listOf("alice", "bob", "carol").forEach { repositories.followingRepository.add(accounts.getValue(it), ACTOR, NOW) }
        repository.touch(accounts.getValue("alice"), NOW)
        repository.touch(accounts.getValue("bob"), NOW.minusSeconds(604800))
        repository.touch(accounts.getValue("carol"), NOW.minusSeconds(604801))
        val public = assertNotNull(repository.receive(note("public", "public", setOf("dave"))))
        assertEquals(listOf("alice", "bob", "dave").map { accounts.getValue(it) }, repository.recipients(public.member, NOW.minusSeconds(604800), null, 1000))
        val followers = assertNotNull(repository.receive(note("followers", "followers", setOf())))
        assertEquals(listOf("alice", "bob").map { accounts.getValue(it) }, repository.recipients(followers.member, NOW.minusSeconds(604800), null, 1000))
        val direct = assertNotNull(repository.receive(note("direct", "direct", setOf("dave"))))
        assertEquals(listOf(accounts.getValue("dave")), repository.recipients(direct.member, NOW.minusSeconds(604800), null, 1000))
        assertTrue(repository.hydrate(accounts.getValue("alice"), listOf(direct.member)).isEmpty())
        repositories.followingRepository.remove(accounts.getValue("alice"), ACTOR)
        assertTrue(repository.hydrate(accounts.getValue("alice"), listOf(followers.member)).isEmpty())
    }

    @Test
    fun rebuildLimitsEachActorAndCursorPreservesLexicalTies() = withRepository { repositories, accounts ->
        val account = accounts.getValue("alice")
        val repository = repositories.timelineRepository
        repositories.followingRepository.add(account, ACTOR, NOW)
        val positions = (1..430).map { assertNotNull(repository.receive(note(it.toString(), "public", setOf()))) }
        assertEquals(20, repository.rebuild(account, 400).size)
        val expected = positions.sortedByDescending { it.member }
        val first = repository.page(account, null, 400)
        val rest = repository.page(account, first.last(), 100)
        assertEquals(expected, first + rest)
        assertEquals(30, rest.size)
        assertEquals(400, repository.hydrate(account, first.map { it.member }).size)
    }

    @Test
    fun followResponsesMatchBothActorAndOriginalActivity() = withRepository { repositories, accounts ->
        val account = accounts.getValue("alice")
        val repository = repositories.timelineRepository
        repositories.followingRepository.add(account, ACTOR, NOW)
        repositories.followingRepository.recordActivity(account, ACTOR, "follow-1")
        repositories.deliveryQueueRepository.enqueueFollow(account, "$ACTOR/inbox", "follow", NOW)
        assertEquals("follow-1", repositories.followingRepository.followId(account, ACTOR))
        assertEquals("follow", repositories.deliveryQueueRepository.claim(NOW, 10).single().body)
        repositories.followingRepository.respondToFollow(ACTOR, "old-follow", null)
        assertTrue(repositories.followingRepository.contains(account, ACTOR))
        repositories.followingRepository.respondToFollow(ACTOR, "follow-1", NOW)
        repositories.followingRepository.respondToFollow("https://other.example/actor", "follow-1", null)
        assertTrue(repositories.followingRepository.contains(account, ACTOR))
        repositories.followingRepository.respondToFollow(ACTOR, "follow-1", null)
        assertEquals(false, repositories.followingRepository.contains(account, ACTOR))
    }

    @Test
    fun localPostsUseEpochMillisecondsAndOnlyReachTheirAuthor() = withRepository { repositories, accounts ->
        val time = NOW.plusNanos(999999999)
        repositories.deliveryQueueRepository.enqueueNote(
            NotePost("alice", "request", PublicNoteId("local-1"), "本文", time, "create", listOf()),
        )
        val repository = repositories.timelineRepository
        val position = assertNotNull(repository.localPosition("local-1"))
        assertEquals(time.toEpochMilli(), position.score)
        assertEquals(listOf(accounts.getValue("alice")), repository.recipients(position.member, NOW, null, 1000))
        assertEquals(listOf(position), repository.rebuild(accounts.getValue("alice"), 400))
        assertEquals(listOf(position), repository.page(accounts.getValue("alice"), null, 10))
        assertEquals("alice", repository.hydrate(accounts.getValue("alice"), listOf(position.member)).single().username)
        assertTrue(repository.hydrate(accounts.getValue("bob"), listOf(position.member)).isEmpty())
    }

    @Test
    fun boundedSourcesUseClampedScoreAndCursorBeforeTheirLimits() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val alice = accounts.getValue("alice")
        val bob = accounts.getValue("bob")
        repositories.followingRepository.add(alice, ACTOR, NOW)
        repositories.followingRepository.add(bob, ACTOR, NOW)
        val positions = (1..70).map { index ->
            val input = note("score-$index", "public", setOf()).copy(
                publishedAt = NOW.plusSeconds(10000L - index),
                receivedAt = NOW.plusSeconds(index.toLong()),
            )
            checkNotNull(repository.receive(input))
        }
        (1..120).forEach { index ->
            repository.receive(note("private-$index", "direct", setOf("bob")))
        }
        assertEquals(positions.reversed().take(20), repository.rebuild(alice, 400))
        val first = repository.page(alice, null, 7)
        val second = repository.page(alice, first.last(), 7)
        assertEquals(positions.reversed().take(14), first + second)
        val ownDirect = checkNotNull(repository.receive(note("own-direct", "direct", setOf("alice"))))
        assertEquals(positions.reversed().take(20), repository.rebuild(alice, 400))
        assertTrue(ownDirect in repository.page(alice, positions.reversed()[19], 100))
        assertEquals(false, repository.shouldReceive("https://unknown.example/actor", setOf()))
        assertTrue(repository.shouldReceive("https://unknown.example/actor", setOf("alice")))
        assertTrue(repository.shouldReceive(ACTOR, setOf()))
    }

    @Test
    fun recipientUnionDeduplicatesAndUsesAccountCursor() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val alice = accounts.getValue("alice")
        repositories.followingRepository.add(alice, ACTOR, NOW)
        repository.touch(alice, NOW)
        val position = checkNotNull(repository.receive(note("recipient-union", "public", setOf("alice", "dave"))))
        val first = repository.recipients(position.member, NOW.minusSeconds(604800), null, 1)
        val second = repository.recipients(position.member, NOW.minusSeconds(604800), first.single(), 1)
        assertEquals(listOf(alice, accounts.getValue("dave")), first + second)
        assertTrue(repository.recipients(position.member, NOW.minusSeconds(604800), second.single(), 1).isEmpty())
    }

    @Test
    fun addressedNotesPageWithoutFollowingTheirActor() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val positions = (1..45).map { index -> checkNotNull(repository.receive(note("address-$index", "direct", setOf("alice")))) }
        val expected = positions.sortedByDescending { it.member }
        val first = repository.page(accounts.getValue("alice"), null, 12)
        val second = repository.page(accounts.getValue("alice"), first.last(), 12)
        assertEquals(expected.take(24), first + second)
        assertEquals(expected, repository.rebuild(accounts.getValue("alice"), 400))
        assertTrue(repository.page(accounts.getValue("bob"), null, 100).isEmpty())
    }

    @Test
    fun rebuiltPrefixAndSqlContinuationIncludeEveryPostFromBusyActors() = withRepository { repositories, accounts ->
        val repository = repositories.timelineRepository
        val account = accounts.getValue("alice")
        val olderActor = "https://older.example/actor"
        repositories.remoteActorRepository.save(
            RemoteActorRecord(olderActor, "$olderActor/inbox", null, "key", RemoteActorProfileRecord("older", null, null, null)),
            NOW,
        )
        listOf(ACTOR, olderActor).forEach { repositories.followingRepository.add(account, it, NOW) }
        val newer = (1..30).map { index ->
            val published = NOW.minusSeconds(index.toLong())
            checkNotNull(repository.receive(note("newer-$index", "public", setOf()).copy(publishedAt = published)))
        }
        val older = (1..20).map { index ->
            checkNotNull(
                repository.receive(
                    note("older-$index", "public", setOf()).copy(
                        actorUri = olderActor,
                        objectUri = "$olderActor/notes/$index",
                        publishedAt = NOW.minusSeconds(100L + index),
                    ),
                ),
            )
        }
        val rebuilt = repository.rebuild(account, 400)
        assertEquals(newer.take(20), rebuilt)
        val allPages = buildList {
            var cursor: TimelinePosition? = null
            do {
                val pageCursor = cursor
                val cached = rebuilt.filter { position ->
                    pageCursor == null || position.score < pageCursor.score ||
                        (position.score == pageCursor.score && position.member < pageCursor.member)
                }.take(7)
                val page = cached + repository.page(account, cached.lastOrNull() ?: pageCursor, 7 - cached.size)
                addAll(page)
                cursor = page.lastOrNull()
            } while (cursor != null)
        }
        assertEquals(newer + older, allPages)
        assertEquals(newer + older, repository.page(account, null, 100))
    }

    private fun note(id: String, visibility: String, recipients: Set<String>): RemoteNoteInput = RemoteNoteInput(
        objectUri = "$ACTOR/notes/$id", actorUri = ACTOR, contentHtml = "<p>本文</p>", visibility = visibility,
        localRecipients = recipients, inReplyTo = null, summary = null, sensitive = false, publishedAt = NOW.plusSeconds(3600), receivedAt = NOW,
    )

    private fun withRepository(block: (Repositories, Map<String, AccountId>) -> Unit) {
        val directory = createTempDirectory("timeline-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            listOf("alice", "bob", "carol", "dave").forEach { username ->
                Accounts.insert(path, username, "hash", "type", "public-key", "private-key", NOW.toString())
            }
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                repositories.remoteActorRepository.save(
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null)),
                    NOW,
                )
                block(repositories, listOf("alice", "bob", "carol", "dave").associateWith { checkNotNull(repositories.accountRepository.findByUsername(it)).id })
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
