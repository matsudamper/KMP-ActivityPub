package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId

class FollowDeliveryQueueRepositoryTest {
    @Test
    fun unfollowCancelsRetryingFollowAndEnqueuesUndoBecauseItMayHaveArrived() = withQueue { queue, alice, bob ->
        queue.enqueueFollow(alice, INBOX, followBody("follow"), NOW)
        val follow = queue.claim(NOW, 10).single()
        queue.scheduleRetry(follow.id, NOW.plusSeconds(3600), "接続失敗")
        queue.enqueueFollow(bob, INBOX, followBody("other-account"), NOW)
        queue.enqueueFollow(alice, "$INBOX/other", followBody("other-inbox"), NOW)
        queue.cancelPendingFollowAndEnqueueUndo(alice, INBOX, "follow", "undo", NOW)
        assertEquals(listOf(followBody("other-account"), followBody("other-inbox"), "undo"), queue.claim(NOW.plusSeconds(7200), 10).map { it.body })
        assertTrue(queue.claim(NOW.plusSeconds(10800), 10).isEmpty())
    }

    @Test
    fun unfollowEnqueuesUndoAfterFollowWasDelivered() = withQueue { queue, alice, _ ->
        queue.enqueueFollow(alice, INBOX, followBody("follow"), NOW)
        queue.markDelivered(queue.claim(NOW, 10).single().id, NOW)
        queue.cancelPendingFollowAndEnqueueUndo(alice, INBOX, "follow", "undo", NOW)
        assertEquals(listOf("undo"), queue.claim(NOW, 10).map { it.body })
    }

    @Test
    fun unfollowKeepsDeliveringFollowAndEnqueuesUndoEvenWhenPendingFollowIsRemoved() = withQueue { queue, alice, _ ->
        queue.enqueueFollow(alice, INBOX, followBody("follow"), NOW)
        val delivering = queue.claim(NOW, 10).single()
        queue.enqueueFollow(alice, INBOX, followBody("follow"), NOW)
        queue.cancelPendingFollowAndEnqueueUndo(alice, INBOX, "follow", "undo", NOW)
        assertEquals(listOf("undo"), queue.claim(NOW, 10).map { it.body })
        assertEquals(2, queue.recoverDelivering(NOW))
        assertEquals(listOf(followBody("follow"), "undo"), queue.claim(NOW, 10).map { it.body })
        queue.markDelivered(delivering.id, NOW)
    }

    @Test
    fun sameInboxFollowForAnotherActorDoesNotCancelOrSuppressUndo() = withQueue { queue, alice, _ ->
        queue.enqueueFollow(alice, INBOX, followBody("actor-a"), NOW)
        queue.markDelivered(queue.claim(NOW, 10).single().id, NOW)
        queue.enqueueFollow(alice, INBOX, followBody("actor-b"), NOW)
        queue.cancelPendingFollowAndEnqueueUndo(alice, INBOX, "actor-a", "undo-a", NOW)
        assertEquals(listOf(followBody("actor-b"), "undo-a"), queue.claim(NOW, 10).map { it.body })
    }

    @Test
    fun sameInboxPendingFollowCancellationPreservesOtherActorsFollow() = withQueue { queue, alice, _ ->
        queue.enqueueFollow(alice, INBOX, followBody("actor-a"), NOW)
        queue.enqueueFollow(alice, INBOX, followBody("actor-b"), NOW)
        queue.cancelPendingFollowAndEnqueueUndo(alice, INBOX, "actor-a", "undo-a", NOW)
        assertEquals(listOf(followBody("actor-b")), queue.claim(NOW, 10).map { it.body })
    }

    @Test
    fun otherActorsDeliveringFollowDoesNotCauseUnnecessaryUndo() = withQueue { queue, alice, _ ->
        queue.enqueueFollow(alice, INBOX, followBody("actor-b"), NOW)
        queue.claim(NOW, 10)
        queue.enqueueFollow(alice, INBOX, followBody("actor-a"), NOW)
        queue.cancelPendingFollowAndEnqueueUndo(alice, INBOX, "actor-a", "undo-a", NOW)
        assertTrue(queue.claim(NOW, 10).isEmpty())
    }

    private fun followBody(id: String): String = """{"id":"$id","type":"Follow"}"""

    private fun withQueue(block: (DeliveryQueueRepository, AccountId, AccountId) -> Unit) {
        val directory = createTempDirectory("follow-delivery-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(path, username, "hash", "type", "public-key", "private-key", NOW.toString())
            }
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                block(
                    repositories.deliveryQueueRepository,
                    checkNotNull(repositories.accountRepository.findByUsername("alice")).id,
                    checkNotNull(repositories.accountRepository.findByUsername("bob")).id,
                )
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val INBOX = "https://remote.example/inbox"
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
