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
        assertEquals(
            BoostTarget.Remote(objectUri = "$ACTOR/notes/public", authorActorUri = ACTOR, authorInbox = "$ACTOR/inbox", unlisted = false),
            boosts.target(alice, member),
        )

        val position = assertNotNull(boosts.boostAndEnqueue(boost(alice, member, "announce-1")))
        assertNull(boosts.boostAndEnqueue(boost(alice, member, "announce-2")))

        assertEquals(true, repositories.timelineRepository.hydrate(alice, listOf(member)).single().boostedByMe)
        assertEquals(false, repositories.timelineRepository.hydrate(bob, listOf(member)).single().boostedByMe)
        assertEquals(
            setOf(FOLLOWER_INBOX to announceBody("announce-1"), "$ACTOR/inbox" to announceBody("announce-1")),
            repositories.deliveryQueueRepository.claim(NOW, 10).map { it.inbox to it.body }.toSet(),
        )
        assertTrue(repositories.timelineRepository.recipients(position.member, NOW.minusSeconds(60), null, 10).isEmpty())
    }

    @Test
    fun ownBoostIsNotShownInTheHomeTimelineButCanBeOpened() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val position = assertNotNull(repositories.accountBoostRepository.boostAndEnqueue(boost(alice, member, "announce-1")))

        assertTrue(repositories.timelineRepository.rebuild(alice, null, 10).none { it.member == position.member })
        val post = repositories.timelineRepository.hydrate(alice, listOf(position.member)).single()
        assertEquals("alice", post.boostedBy?.username)
        assertNull(post.boostedBy?.actorUri)
        assertEquals(member, post.reactionMember)
        assertEquals(true, post.boostedByMe)
    }

    @Test
    fun unlistedPostCannotBeBoosted() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "unlisted", alice)

        assertNull(repositories.accountBoostRepository.target(alice, member))
        assertNull(repositories.timelineRepository.hydrate(alice, listOf(member)).single().boostedByMe)
    }

    @Test
    fun followersOnlyOrMissingPostCannotBeBoosted() = withRepository { repositories, alice, _ ->
        val followersOnly = receiveNote(repositories, "followers", alice)

        assertNull(repositories.accountBoostRepository.target(alice, followersOnly))
        assertNull(repositories.accountBoostRepository.target(alice, "r999"))
        assertNull(repositories.accountBoostRepository.target(alice, "l999"))
    }

    @Test
    fun localPostCanBeBoostedByItsAuthorAndOthers() = withRepository { repositories, alice, bob ->
        val local = postLocalNote(repositories)
        val boosts = repositories.accountBoostRepository
        assertEquals(BoostTarget.Local(publicId = PublicNoteId("bob-note"), authorUsername = "bob"), boosts.target(bob, local))
        assertEquals(BoostTarget.Local(publicId = PublicNoteId("bob-note"), authorUsername = "bob"), boosts.target(alice, local))
        assertEquals(false, repositories.timelineRepository.hydrate(bob, listOf(local)).single().boostedByMe)

        assertNotNull(boosts.boostAndEnqueue(boost(bob, local, "announce-bob")))
        assertNotNull(boosts.boostAndEnqueue(boost(alice, local, "announce-alice")))

        assertEquals(true, repositories.timelineRepository.hydrate(bob, listOf(local)).single().boostedByMe)
        assertEquals(setOf(PublicNoteId("bob-note")), boosts.boostedLocalNotes(alice, setOf(PublicNoteId("bob-note"))))
        assertEquals(mapOf(PublicNoteId("bob-note") to 2L), repositories.boostRepository.counts(setOf(PublicNoteId("bob-note"))))
    }

    @Test
    fun deletingLocalPostUndoesItsBoosts() = withRepository { repositories, alice, _ ->
        val local = postLocalNote(repositories)
        val boosts = repositories.accountBoostRepository
        val queue = repositories.deliveryQueueRepository
        boosts.boostAndEnqueue(boost(alice, local, "announce-1"))
        queue.claim(NOW, 10).forEach { queue.markDelivered(it.id, NOW) }

        assertTrue(
            queue.enqueueNoteDeletion(
                NoteDeletion(username = "bob", publicId = PublicNoteId("bob-note"), deletedAt = NOW, body = "delete", inboxes = listOf()),
            ),
        )

        assertEquals(INBOXES.toSet(), queue.claim(NOW, 10).onEach { assertEquals(undoBody("announce-1"), it.body) }.map { it.inbox }.toSet())
        assertTrue(boosts.boostedLocalNotes(alice, setOf(PublicNoteId("bob-note"))).isEmpty())
    }

    @Test
    fun unboostBeforeAnnounceIsSentCancelsAnnounceWithoutUndo() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val boosts = repositories.accountBoostRepository
        boosts.boostAndEnqueue(boost(alice, member, "announce-1"))

        assertTrue(boosts.unboostAndEnqueueUndo(alice, member, NOW))

        assertTrue(repositories.deliveryQueueRepository.claim(NOW, 10).isEmpty())
        assertEquals(false, repositories.timelineRepository.hydrate(alice, listOf(member)).single().boostedByMe)
        assertFalse(boosts.unboostAndEnqueueUndo(alice, member, NOW))
    }

    @Test
    fun unboostByTheOwnBoostMemberRemovesTheBoost() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val boosts = repositories.accountBoostRepository
        val position = assertNotNull(boosts.boostAndEnqueue(boost(alice, member, "announce-1")))

        assertTrue(boosts.unboostAndEnqueueUndo(alice, position.member, NOW))

        assertEquals(false, repositories.timelineRepository.hydrate(alice, listOf(member)).single().boostedByMe)
    }

    @Test
    fun unboostSendsUndoOnlyToInboxesTheAnnounceMayHaveReached() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val boosts = repositories.accountBoostRepository
        val queue = repositories.deliveryQueueRepository
        boosts.boostAndEnqueue(boost(alice, member, "announce-1"))
        val sent = queue.claim(NOW, 1).single()
        queue.markDelivered(sent.id, NOW)

        assertTrue(boosts.unboostAndEnqueueUndo(alice, member, NOW))

        assertEquals(listOf(sent.inbox to undoBody("announce-1")), queue.claim(NOW, 10).map { it.inbox to it.body })
    }

    @Test
    fun unboostAfterUnfollowingTheAuthorStillSendsUndo() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val boosts = repositories.accountBoostRepository
        val queue = repositories.deliveryQueueRepository
        boosts.boostAndEnqueue(boost(alice, member, "announce-1"))
        queue.claim(NOW, 10).forEach { queue.markDelivered(it.id, NOW) }
        repositories.followingRepository.remove(alice, ACTOR)

        assertTrue(boosts.unboostAndEnqueueUndo(alice, member, NOW))

        assertEquals(INBOXES.toSet(), queue.claim(NOW, 10).map { it.inbox }.toSet())
    }

    @Test
    fun postSeenThroughABoostCanBeBoostedAndUnboostedAfterTheReceivedBoostIsUndone() = withRepository { repositories, alice, bob ->
        val boostMember = receiveBoost(repositories, alice)
        val reactionMember = repositories.timelineRepository.hydrate(alice, listOf(boostMember)).single().reactionMember
        val boosts = repositories.accountBoostRepository
        val queue = repositories.deliveryQueueRepository

        assertNotNull(boosts.target(alice, reactionMember))
        assertNotNull(boosts.boostAndEnqueue(boost(alice, reactionMember, "announce-1")))
        queue.claim(NOW, 10).forEach { queue.markDelivered(it.id, NOW) }
        assertNotNull(repositories.boostRepository.remove(BOOSTER, "$BOOSTER/announces/1", NOW))

        assertTrue(boosts.unboostAndEnqueueUndo(alice, reactionMember, NOW))
        assertEquals(INBOXES.toSet(), queue.claim(NOW, 10).map { it.inbox }.toSet())
    }

    @Test
    fun deletingNoteUndoesBoostsOnlyWhenTheAuthorDeletesIt() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, "public", alice, bob)
        val boosts = repositories.accountBoostRepository
        val queue = repositories.deliveryQueueRepository
        boosts.boostAndEnqueue(boost(alice, member, "announce-1"))
        queue.claim(NOW, 10).forEach { queue.markDelivered(it.id, NOW) }

        assertEquals(RemoteNoteDeletion(undoneBoostCount = 0, deletedTimelineMembers = listOf()), boosts.deleteRemoteNoteAndUndoBoosts("https://other.example/actor", "$ACTOR/notes/public", NOW))
        assertTrue(queue.claim(NOW, 10).isEmpty())
        assertEquals(true, repositories.timelineRepository.hydrate(alice, listOf(member)).single().boostedByMe)

        assertEquals(RemoteNoteDeletion(undoneBoostCount = 1, deletedTimelineMembers = listOf(member)), boosts.deleteRemoteNoteAndUndoBoosts(ACTOR, "$ACTOR/notes/public", NOW))
        assertEquals(INBOXES.toSet(), queue.claim(NOW, 10).onEach { assertEquals(undoBody("announce-1"), it.body) }.map { it.inbox }.toSet())
        assertTrue(repositories.timelineRepository.hydrate(alice, listOf(member)).single().deleted)
        // 削除済みと出すために残した行には、新しくブーストもいいねも付けられない
        assertNull(boosts.target(alice, member))
        assertNull(repositories.likeRepository.target(alice, member))
    }

    @Test
    fun announceBeingDeliveredIsNotRetriedAfterUnboost() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val boosts = repositories.accountBoostRepository
        val queue = repositories.deliveryQueueRepository
        boosts.boostAndEnqueue(boost(alice, member, "announce-1"))
        val delivering = queue.claim(NOW, 10)

        assertTrue(boosts.unboostAndEnqueueUndo(alice, member, NOW))
        delivering.forEach { queue.scheduleRetry(it.id, NOW, "接続失敗") }

        assertEquals(
            setOf(FOLLOWER_INBOX, "$ACTOR/inbox"),
            queue.claim(NOW, 10).onEach { assertEquals(undoBody("announce-1"), it.body) }.map { it.inbox }.toSet(),
        )
    }

    @Test
    fun notesFetchedFromAnUnfollowedProfileCanBeLikedAndBoosted() = withRepository { repositories, alice, bob ->
        val stored = repositories.timelineRepository.storeFetchedNotes(alice, listOf(note("public"), note("unlisted"), note("followers")))

        assertEquals(setOf("$ACTOR/notes/public", "$ACTOR/notes/unlisted"), stored.keys)
        val member = assertNotNull(stored["$ACTOR/notes/public"]).reactionMember
        assertEquals(false, stored["$ACTOR/notes/public"]?.boostedByMe)
        assertNotNull(repositories.likeRepository.target(alice, member))
        assertNotNull(repositories.accountBoostRepository.target(bob, member))
        assertNotNull(repositories.accountBoostRepository.boostAndEnqueue(boost(alice, member, "announce-1")))
        assertTrue(repositories.timelineRepository.rebuild(bob, null, 10).none { it.member == member })

        val storedAgain = repositories.timelineRepository.storeFetchedNotes(alice, listOf(note("public")))
        assertEquals(member, storedAgain["$ACTOR/notes/public"]?.reactionMember)
        assertEquals(true, storedAgain["$ACTOR/notes/public"]?.boostedByMe)
    }

    @Test
    fun refetchedNoteReplacesTheStoredContentOnlyForTheSameAuthor() = withRepository { repositories, alice, _ ->
        val timeline = repositories.timelineRepository
        val member = assertNotNull(timeline.storeFetchedNotes(alice, listOf(note("public")))["$ACTOR/notes/public"]).reactionMember

        val edited = timeline.storeFetchedNotes(alice, listOf(note("public").copy(contentHtml = "<p>編集後</p>", summary = "注意", sensitive = true)))
        assertEquals(member, edited["$ACTOR/notes/public"]?.reactionMember)
        assertEquals("<p>編集後</p>", edited["$ACTOR/notes/public"]?.contentHtml)
        assertEquals("注意", edited["$ACTOR/notes/public"]?.summary)

        repositories.remoteActorRepository.save(
            RemoteActorRecord(BOOSTER, "$BOOSTER/inbox", null, "key", RemoteActorProfileRecord("booster", "Booster", null, null, null, listOf())),
            NOW,
        )
        assertTrue(timeline.storeFetchedNotes(alice, listOf(note("public").copy(actorUri = BOOSTER, contentHtml = "<p>なりすまし</p>"))).isEmpty())
        repositories.followingRepository.add(alice, ACTOR, NOW)
        assertEquals("<p>編集後</p>", timeline.hydrate(alice, listOf(member)).single().contentHtml)
    }

    @Test
    fun removingTheAuthorUndoesBoostsOfTheirNotes() = withRepository { repositories, alice, _ ->
        val member = receiveNote(repositories, "public", alice)
        val queue = repositories.deliveryQueueRepository
        repositories.accountBoostRepository.boostAndEnqueue(boost(alice, member, "announce-1"))
        queue.claim(NOW, 10).forEach { queue.markDelivered(it.id, NOW) }

        repositories.followerRepository.removeRemoteActor(ACTOR, NOW)

        assertEquals(INBOXES.toSet(), queue.claim(NOW, 10).onEach { assertEquals(undoBody("announce-1"), it.body) }.map { it.inbox }.toSet())
        assertFalse(repositories.timelineRepository.hasRemoteNote("$ACTOR/notes/public"))
    }

    @Test
    fun fetchedNoteWhoseIdIsOnAnotherHostIsNotStored() = withRepository { repositories, alice, _ ->
        val otherHost = "https://other.example/notes/1"
        val otherPort = "https://remote.example:8443/notes/1"
        val stored = repositories.timelineRepository.storeFetchedNotes(
            alice,
            listOf(note("public").copy(objectUri = otherHost), note("public").copy(objectUri = otherPort)),
        )

        assertTrue(stored.isEmpty())
        assertFalse(repositories.timelineRepository.hasRemoteNote(otherHost))
        assertFalse(repositories.timelineRepository.hasRemoteNote(otherPort))
    }

    @Test
    fun refetchedNoteTakesTheNewVisibility() = withRepository { repositories, alice, _ ->
        val timeline = repositories.timelineRepository
        val member = assertNotNull(timeline.storeFetchedNotes(alice, listOf(note("public")))["$ACTOR/notes/public"]).reactionMember

        repositories.accountBoostRepository.boostAndEnqueue(boost(alice, member, "announce-1"))

        val refetched = timeline.storeFetchedNotes(alice, listOf(note("public").copy(visibility = "unlisted")))

        assertNull(repositories.accountBoostRepository.target(alice, member))
        // 付けたブーストは公開範囲が変わっても外せる
        assertEquals(true, refetched["$ACTOR/notes/public"]?.boostedByMe)
        assertTrue(repositories.accountBoostRepository.unboostAndEnqueueUndo(alice, member, NOW))
    }

    @Test
    fun outboxListsBoostsNewestFirstAndContinuesAfterThePosition() = withRepository { repositories, alice, _ ->
        val public = receiveNote(repositories, "public", alice)
        val unlisted = assertNotNull(repositories.timelineRepository.receive(note("unlisted"))).member
        val boosts = repositories.accountBoostRepository
        boosts.boostAndEnqueue(boost(alice, public, "announce-1", boostedAt = NOW))
        boosts.boostAndEnqueue(boost(alice, unlisted, "announce-2", boostedAt = NOW.plusSeconds(1), unlisted = true))

        assertEquals(2, boosts.countForOutbox("alice"))
        assertEquals(0, boosts.countForOutbox("bob"))
        val first = boosts.listForOutbox("alice", after = null, limit = 1).single()
        assertEquals(
            AccountBoostRecord(announceId = "announce-2", objectUri = "$ACTOR/notes/unlisted", objectAuthor = ACTOR, unlisted = true, boostedAt = NOW.plusSeconds(1)),
            first,
        )
        assertEquals(
            listOf("announce-1"),
            boosts.listForOutbox("alice", after = AccountBoostPosition(first.boostedAt, first.announceId), limit = 10).map { it.announceId },
        )
    }

    @Test
    fun accountBoostsAreShownToOtherViewersWithTheirOwnReactions() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, "public", alice, bob)
        repositories.accountBoostRepository.boostAndEnqueue(boost(alice, member, "announce-1"))

        val seenByBob = repositories.timelineRepository.accountBoosts(booster = alice, viewer = bob, limit = 10).single()
        assertEquals("alice", seenByBob.boostedBy?.username)
        assertEquals(false, seenByBob.boostedByMe)
        assertEquals(1, repositories.timelineRepository.accountBoosts(booster = alice, viewer = null, limit = 10).size)
        assertTrue(repositories.timelineRepository.accountBoosts(booster = bob, viewer = null, limit = 10).isEmpty())
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
                inReplyTo = null,
                mentions = listOf(),
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
            RemoteActorRecord(BOOSTER, "$BOOSTER/inbox", null, "key", RemoteActorProfileRecord("booster", "Booster", null, null, null, listOf())),
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

    private fun boost(
        accountId: AccountId,
        member: String,
        announceId: String,
        boostedAt: Instant = NOW,
        unlisted: Boolean = false,
    ): OutgoingBoost = OutgoingBoost(
        accountId = accountId,
        member = member,
        announceId = announceId,
        announceBody = announceBody(announceId),
        undoBody = undoBody(announceId),
        inboxes = INBOXES,
        objectUri = "$ACTOR/notes/${if (unlisted) "unlisted" else "public"}",
        objectAuthor = ACTOR,
        unlisted = unlisted,
        boostedAt = boostedAt,
    )

    private fun announceBody(id: String): String = """{"id":"$id","type":"Announce"}"""

    private fun undoBody(id: String): String = """{"type":"Undo","object":"$id"}"""

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
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null, null, listOf())),
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
