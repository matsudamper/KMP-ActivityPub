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

class LikeRepositoryTest {
    @Test
    fun likeIsRecordedOnceAndShownOnlyToTheAccountThatLiked() = withRepository { repositories, alice, bob ->
        val member = receiveNote(repositories, alice, bob)
        val likes = repositories.likeRepository
        val target = assertNotNull(likes.target(alice, member))
        assertEquals(LikeTarget(objectUri = "$ACTOR/notes/1", inbox = "$ACTOR/inbox"), target)

        assertTrue(likes.likeAndEnqueue(like(alice, member, "like-1")))
        assertFalse(likes.likeAndEnqueue(like(alice, member, "like-2")))

        assertEquals(true, repositories.timelineRepository.hydrate(alice, listOf(member)).single().likedByMe)
        assertEquals(false, repositories.timelineRepository.hydrate(bob, listOf(member)).single().likedByMe)
        assertEquals(listOf(likeBody("like-1")), repositories.deliveryQueueRepository.claim(NOW, 10).map { it.body })
    }

    @Test
    fun unreadableOrLocalPostCannotBeLiked() = withRepository { repositories, alice, _ ->
        val directOnly = assertNotNull(repositories.timelineRepository.receive(note("direct", "direct", setOf("bob")))).member
        assertNull(repositories.likeRepository.target(alice, directOnly))
        assertNull(repositories.likeRepository.target(alice, "l1"))
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
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
