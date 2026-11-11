package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class AccountBoostRepositoryTest {
    @Test
    fun boostIsRecordedOnceAndShownOnlyToTheAccountThatBoosted() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, "public", alice, bob)
        val boosts = repositories.accountBoostRepository
        assertEquals(BoostTarget(objectUri = "$ACTOR/notes/public", authorActorUri = ACTOR, authorInbox = "$ACTOR/inbox"), boosts.target(alice, member))

        assertTrue(boosts.boostAndEnqueue(boost(alice, member, "announce-1")))
        assertFalse(boosts.boostAndEnqueue(boost(alice, member, "announce-2")))

        assertEquals(true, repositories.timelineRepository.hydrate(alice, listOf(member)).single().boostedByMe)
        assertEquals(false, repositories.timelineRepository.hydrate(bob, listOf(member)).single().boostedByMe)
        assertEquals(
            setOf(FOLLOWER_INBOX to announceBody("announce-1"), "$ACTOR/inbox" to announceBody("announce-1")),
            repositories.deliveryQueueRepository.claim(NOW, 10).map { it.inbox to it.body }.toSet(),
        )
    }

    @Test
    fun followersOnlyLocalOrMissingPostCannotBeBoosted() = withRepository { repositories, alice, bob ->
        val followersOnly = receiveNote(repositories, "followers", alice)
        val local = postLocalNote(repositories)

        assertNull(repositories.accountBoostRepository.target(alice, followersOnly))
        assertNull(repositories.accountBoostRepository.target(alice, local))
        assertNull(repositories.accountBoostRepository.target(alice, "r999"))
        assertNull(repositories.timelineRepository.hydrate(bob, listOf(local)).single().boostedByMe)
    }

    @Test
    fun unboostBeforeAnnounceIsSentCancelsAnnounceWithoutUndo() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val boosts = repositories.accountBoostRepository
        boosts.boostAndEnqueue(boost(alice, member, "announce-1"))

        assertTrue(boosts.unboostAndEnqueueUndo(alice, member, NOW) { "undo:$it" })

        assertTrue(repositories.deliveryQueueRepository.claim(NOW, 10).isEmpty())
        assertEquals(false, repositories.timelineRepository.hydrate(alice, listOf(member)).single().boostedByMe)
        assertFalse(boosts.unboostAndEnqueueUndo(alice, member, NOW) { "undo:$it" })
    }

    @Test
    fun unboostSendsUndoOnlyToInboxesTheAnnounceMayHaveReached() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val boosts = repositories.accountBoostRepository
        val queue = repositories.deliveryQueueRepository
        boosts.boostAndEnqueue(boost(alice, member, "announce-1"))
        val sent = queue.claim(NOW, 1).single()
        queue.markDelivered(sent.id, NOW)

        assertTrue(boosts.unboostAndEnqueueUndo(alice, member, NOW) { "undo:$it" })

        assertEquals(listOf(sent.inbox to "undo:${announceBody("announce-1")}"), queue.claim(NOW, 10).map { it.inbox to it.body })
    }

    @Test
    fun unboostAfterUnfollowingTheAuthorStillSendsUndo() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val boosts = repositories.accountBoostRepository
        val queue = repositories.deliveryQueueRepository
        boosts.boostAndEnqueue(boost(alice, member, "announce-1"))
        queue.claim(NOW, 10).forEach { queue.markDelivered(it.id, NOW) }
        repositories.followingRepository.remove(alice, ACTOR)

        assertNull(boosts.target(alice, member))
        assertTrue(boosts.unboostAndEnqueueUndo(alice, member, NOW) { "undo:$it" })

        assertEquals(INBOXES.toSet(), queue.claim(NOW, 10).map { it.inbox }.toSet())
    }

    @Test
    fun postSeenThroughABoostCanBeBoostedAndUnboostedAfterTheReceivedBoostIsUndone() = withRepository { repositories, alice, bob ->
        val boostMember = receiveBoost(repositories, alice)
        val reactionMember = repositories.timelineRepository.hydrate(alice, listOf(boostMember)).single().reactionMember
        val boosts = repositories.accountBoostRepository
        val queue = repositories.deliveryQueueRepository

        assertNotNull(boosts.target(alice, reactionMember))
        assertNull(boosts.target(bob, reactionMember))
        assertTrue(boosts.boostAndEnqueue(boost(alice, reactionMember, "announce-1")))
        queue.claim(NOW, 10).forEach { queue.markDelivered(it.id, NOW) }
        assertTrue(repositories.boostRepository.remove(BOOSTER, "$BOOSTER/announces/1"))

        assertTrue(boosts.unboostAndEnqueueUndo(alice, reactionMember, NOW) { "undo:$it" })
        assertEquals(INBOXES.toSet(), queue.claim(NOW, 10).map { it.inbox }.toSet())
    }

    @Test
    fun announceBeingDeliveredIsNotRetriedAfterUnboost() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val boosts = repositories.accountBoostRepository
        val queue = repositories.deliveryQueueRepository
        boosts.boostAndEnqueue(boost(alice, member, "announce-1"))
        val delivering = queue.claim(NOW, 10)

        assertTrue(boosts.unboostAndEnqueueUndo(alice, member, NOW) { "undo:$it" })
        delivering.forEach { queue.scheduleRetry(it.id, NOW, "接続失敗") }

        assertEquals(
            setOf(FOLLOWER_INBOX, "$ACTOR/inbox"),
            queue.claim(NOW, 10).onEach { assertEquals("undo:${announceBody("announce-1")}", it.body) }.map { it.inbox }.toSet(),
        )
    }

    private fun postLocalNote(repositories: Repositories): String {
        repositories.deliveryQueueRepository.enqueueNote(
            NotePost(
                username = "bob",
                requestId = "request",
                publicId = PublicNoteId("bob-note"),
                contentHtml = "<p>本文</p>",
                publishedAt = NOW,
                images = listOf(),
                body = "create",
                inboxes = listOf(),
            ),
        )
        return checkNotNull(repositories.timelineRepository.localPosition("bob-note")).member
    }

    private fun receiveNote(repositories: Repositories, visibility: String, vararg followers: AccountId): String {
        followers.forEach { repositories.followingRepository.add(it, ACTOR, NOW) }
        return assertNotNull(repositories.timelineRepository.receive(note(visibility))).member
    }

    private fun receiveBoost(repositories: Repositories, follower: AccountId): String {
        repositories.remoteActorRepository.save(
            RemoteActorRecord(BOOSTER, "$BOOSTER/inbox", null, "key", RemoteActorProfileRecord("booster", "Booster", null, null, listOf())),
            NOW,
        )
        repositories.followingRepository.add(follower, BOOSTER, NOW)
        return assertNotNull(
            repositories.timelineRepository.receiveBoost(
                RemoteBoostInput(
                    activityUri = "$BOOSTER/announces/1",
                    boosterUri = BOOSTER,
                    boostedAt = NOW,
                    receivedAt = NOW,
                    note = note("public"),
                ),
            ),
        ).member
    }

    private fun boost(accountId: AccountId, member: String, announceId: String): OutgoingBoost = OutgoingBoost(
        accountId = accountId,
        member = member,
        announceId = announceId,
        inboxes = INBOXES,
        body = announceBody(announceId),
        boostedAt = NOW,
    )

    private fun announceBody(id: String): String = """{"id":"$id","type":"Announce"}"""

    private fun note(id: String): RemoteNoteInput = RemoteNoteInput(
        objectUri = "$ACTOR/notes/$id", actorUri = ACTOR, contentHtml = "<p>本文</p>", visibility = id,
        localRecipients = setOf(), inReplyTo = null, summary = null, sensitive = false, publishedAt = NOW, receivedAt = NOW,
        images = listOf(),
        emojis = listOf(),
    )

    private fun withRepository(block: (Repositories, AccountId, AccountId) -> Unit) {
        val directory = createTempDirectory("boost-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(path, username, "hash", "type", "public-key", "private-key", NOW.toString())
            }
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                repositories.remoteActorRepository.save(
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null, listOf())),
                    NOW,
                )
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
                block(repositories, alice, bob)
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val ACTOR = "https://remote.example/actor"
        const val BOOSTER = "https://booster.example/actor"
        const val FOLLOWER_INBOX = "https://follower.example/inbox"
        val INBOXES = listOf(FOLLOWER_INBOX, "$ACTOR/inbox")
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
