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

class LikeRepositoryTest {
    @Test
    fun likeIsRecordedOnceAndShownOnlyToTheAccountThatLiked() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val target = assertNotNull(likes.target(alice, member))
        assertEquals(LikeTarget.Remote(objectUri = "$ACTOR/notes/1", inbox = "$ACTOR/inbox"), target)

        assertTrue(likes.likeAndEnqueue(like(alice, member, "like-1")))
        assertFalse(likes.likeAndEnqueue(like(alice, member, "like-2")))

        assertEquals(true, repositories.timelineRepository.hydrate(alice, listOf(member)).single().likedByMe)
        assertEquals(false, repositories.timelineRepository.hydrate(bob, listOf(member)).single().likedByMe)
        assertEquals(listOf(likeBody("like-1")), repositories.deliveryQueueRepository.claim(NOW, 10).map { it.body })
    }

    @Test
    fun unreadableOrMissingPostCannotBeLiked() = withRepository { repositories, alice, _ ->
        val directOnly = assertNotNull(repositories.timelineRepository.receive(note("direct", "direct", setOf("bob")))).member
        assertNull(repositories.likeRepository.target(alice, directOnly))
        assertNull(repositories.likeRepository.target(alice, "l999"))
    }

    @Test
    fun localPostOfAnotherAccountAndOwnPostCanBeLikedWithoutDelivery() = withRepository { repositories, alice, bob ->
        val member = postLocalNote(repositories, username = "bob", publicId = "bob-note")
        val likes = repositories.likeRepository
        assertEquals(LikeTarget.Local, likes.target(alice, member))

        assertTrue(likes.likeLocal(alice, member, NOW))
        assertFalse(likes.likeLocal(alice, member, NOW))
        assertTrue(likes.likeLocal(bob, member, NOW))

        assertEquals(true, repositories.timelineRepository.hydrate(bob, listOf(member)).single().likedByMe)
        assertEquals(2L, repositories.timelineRepository.hydrate(bob, listOf(member)).single().favouriteCount)
        assertEquals(mapOf(PublicNoteId("bob-note") to 2L), repositories.favouriteRepository.counts(setOf(PublicNoteId("bob-note"))))
        assertEquals(setOf(PublicNoteId("bob-note")), likes.likedLocalNotes(alice, setOf(PublicNoteId("bob-note"))))
        assertTrue(repositories.deliveryQueueRepository.claim(NOW, 10).isEmpty())
    }

    @Test
    fun unlikeLocalPostRemovesOnlyThatAccountsLike() = withRepository { repositories, alice, bob ->
        val member = postLocalNote(repositories, username = "alice", publicId = "alice-note")
        val likes = repositories.likeRepository
        likes.likeLocal(alice, member, NOW)
        likes.likeLocal(bob, member, NOW)

        assertTrue(likes.unlikeLocal(alice, member))
        assertFalse(likes.unlikeLocal(alice, member))

        val post = repositories.timelineRepository.hydrate(alice, listOf(member)).single()
        assertEquals(false, post.likedByMe)
        assertEquals(1L, post.favouriteCount)
        assertEquals(setOf(), likes.likedLocalNotes(alice, setOf(PublicNoteId("alice-note"))))
    }

    @Test
    fun unlikeBeforeLikeIsSentCancelsLikeWithoutUndo() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, "$ACTOR/inbox", NOW) { "undo:$it" })

        assertTrue(repositories.deliveryQueueRepository.claim(NOW, 10).isEmpty())
        assertEquals(false, repositories.timelineRepository.hydrate(alice, listOf(member)).single().likedByMe)
        assertFalse(likes.unlikeAndEnqueueUndo(alice, member, "$ACTOR/inbox", NOW) { "undo:$it" })
    }

    @Test
    fun unlikeAfterLikeWasSentEnqueuesUndoForThatLike() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))
        queue.markDelivered(queue.claim(NOW, 10).single().id, NOW)

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, "$ACTOR/inbox", NOW) { "undo:$it" })

        assertEquals(listOf("undo:like-1"), queue.claim(NOW, 10).map { it.body })
    }

    @Test
    fun unlikeWhileLikeIsRetryingCancelsRetryAndEnqueuesUndo() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))
        queue.scheduleRetry(queue.claim(NOW, 10).single().id, NOW, "接続失敗")

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, "$ACTOR/inbox", NOW) { "undo:$it" })

        assertEquals(listOf("undo:like-1"), queue.claim(NOW, 10).map { it.body })
    }

    @Test
    fun likeBeingDeliveredIsNotRetriedAfterUnlike() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))
        val delivering = queue.claim(NOW, 10).single()

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, "$ACTOR/inbox", NOW) { "undo:$it" })
        queue.scheduleRetry(delivering.id, NOW, "接続失敗")

        assertEquals(listOf("undo:like-1"), queue.claim(NOW, 10).map { it.body })
    }

    @Test
    fun likeBeingDeliveredIsNotRecoveredAfterUnlike() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val queue = repositories.deliveryQueueRepository
        likes.likeAndEnqueue(like(alice, member, "like-1"))
        queue.claim(NOW, 10)

        assertTrue(likes.unlikeAndEnqueueUndo(alice, member, "$ACTOR/inbox", NOW) { "undo:$it" })
        queue.recoverDelivering(NOW)

        assertEquals(listOf("undo:like-1"), queue.claim(NOW, 10).map { it.body })
    }

    @Test
    fun boostedPostIsLikedAsTheBoostedNoteOnlyWhileTheBoosterIsFollowed() = withRepository { repositories, alice, bob ->
        repositories.remoteActorRepository.save(
            RemoteActorRecord(BOOSTER, "$BOOSTER/inbox", null, "key", RemoteActorProfileRecord("booster", "Booster", null, null)),
            NOW,
        )
        repositories.followingRepository.add(alice, BOOSTER, NOW)
        val boostMember = assertNotNull(
            repositories.timelineRepository.receiveBoost(
                RemoteBoostInput(
                    activityUri = "$BOOSTER/announces/1",
                    boosterUri = BOOSTER,
                    boostedAt = NOW,
                    receivedAt = NOW,
                    note = note("boosted", "public", setOf()),
                ),
            ),
        ).member
        val likes = repositories.likeRepository

        assertEquals(LikeTarget.Remote(objectUri = "$ACTOR/notes/boosted", inbox = "$ACTOR/inbox"), likes.target(alice, boostMember))
        assertNull(likes.target(bob, boostMember))
        assertTrue(likes.likeAndEnqueue(like(alice, boostMember, "like-1")))
        assertEquals(true, repositories.timelineRepository.hydrate(alice, listOf(boostMember)).single().likedByMe)
        assertEquals(listOf(likeBody("like-1")), repositories.deliveryQueueRepository.claim(NOW, 10).map { it.body })

        assertTrue(likes.unlikeAndEnqueueUndo(alice, boostMember, "$ACTOR/inbox", NOW) { "undo:$it" })
        assertEquals(false, repositories.timelineRepository.hydrate(alice, listOf(boostMember)).single().likedByMe)
    }

    private fun postLocalNote(repositories: Repositories, username: String, publicId: String): String {
        repositories.deliveryQueueRepository.enqueueNote(
            NotePost(
                username = username,
                requestId = "request-$publicId",
                publicId = PublicNoteId(publicId),
                contentHtml = "<p>本文</p>",
                publishedAt = NOW,
                images = listOf(),
                body = "create",
                inboxes = listOf(),
            ),
        )
        return checkNotNull(repositories.timelineRepository.localPosition(publicId)).member
    }

    private fun receiveNote(repositories: Repositories, vararg followers: AccountId): String {
        followers.forEach { repositories.followingRepository.add(it, ACTOR, NOW) }
        return assertNotNull(repositories.timelineRepository.receive(note("1", "public", setOf()))).member
    }

    private fun like(accountId: AccountId, member: String, likeId: String): OutgoingLike = OutgoingLike(
        accountId = accountId,
        member = member,
        likeId = likeId,
        inbox = "$ACTOR/inbox",
        body = likeBody(likeId),
        likedAt = NOW,
    )

    private fun likeBody(id: String): String = """{"id":"$id","type":"Like"}"""

    private fun note(id: String, visibility: String, recipients: Set<String>): RemoteNoteInput = RemoteNoteInput(
        objectUri = "$ACTOR/notes/$id", actorUri = ACTOR, contentHtml = "<p>本文</p>", visibility = visibility,
        localRecipients = recipients, inReplyTo = null, summary = null, sensitive = false, publishedAt = NOW, receivedAt = NOW,
        images = listOf(),
    )

    private fun withRepository(block: (Repositories, AccountId, AccountId) -> Unit) {
        val directory = createTempDirectory("like-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(path, username, "hash", "type", "public-key", "private-key", NOW.toString())
            }
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                repositories.remoteActorRepository.save(
                    RemoteActorRecord(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfileRecord("remote", "Remote", null, null)),
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
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
