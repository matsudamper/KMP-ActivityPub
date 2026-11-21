package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.shared.AccountId

class FailedDeliveryRepositoryTest {
    @Test
    fun pageReturnsOnlyGivenUpDeliveriesNewestFirst() = withRepositories { queue, failed, alice ->
        listOf("first", "second", "third").forEach { queue.enqueueFollow(alice, "$INBOX/$it", it, NOW) }
        val (first, second, third) = queue.claim(NOW, 10, setOf())
        queue.giveUp(first.id, error = "410", failedAt = FAILED_AT)
        queue.scheduleRetry(second.id, NOW.plusSeconds(60), "接続失敗")
        queue.giveUp(third.id, error = "403", failedAt = FAILED_AT)

        assertEquals(
            listOf(
                FailedDelivery(
                    id = third.id,
                    kind = "follow",
                    username = "alice",
                    inbox = "$INBOX/third",
                    attempts = 1,
                    enqueuedAt = NOW,
                    failedAt = FAILED_AT,
                    lastError = "403",
                ),
                FailedDelivery(
                    id = first.id,
                    kind = "follow",
                    username = "alice",
                    inbox = "$INBOX/first",
                    attempts = 1,
                    enqueuedAt = NOW,
                    failedAt = FAILED_AT,
                    lastError = "410",
                ),
            ),
            failed.page(beforeId = null, limit = 10),
        )
    }

    @Test
    fun pageContinuesBeforeTheGivenId() = withRepositories { queue, failed, alice ->
        listOf("first", "second", "third").forEach { queue.enqueueFollow(alice, "$INBOX/$it", it, NOW) }
        val claimed = queue.claim(NOW, 10, setOf())
        claimed.forEach { queue.giveUp(it.id, error = "410", failedAt = FAILED_AT) }

        val firstPage = failed.page(beforeId = null, limit = 2)
        assertEquals(listOf("$INBOX/third", "$INBOX/second"), firstPage.map { it.inbox })
        assertEquals(listOf("$INBOX/first"), failed.page(beforeId = firstPage.last().id, limit = 2).map { it.inbox })
    }

    @Test
    fun findReturnsNullUnlessGivenUp() = withRepositories { queue, failed, alice ->
        queue.enqueueFollow(alice, INBOX, "pending", NOW)
        val pending = queue.claim(NOW, 10, setOf()).single()
        assertNull(failed.find(pending.id))

        queue.giveUp(pending.id, error = "410", failedAt = FAILED_AT)
        assertEquals("410", failed.find(pending.id)?.lastError)
    }

    private fun withRepositories(block: (DeliveryQueueRepository, FailedDeliveryRepository, AccountId) -> Unit) {
        val directory = createTempDirectory("failed-delivery-test")
        try {
            val path = directory.resolve("test.db")
            TestSchema.applyTo(path)
            Accounts.insert(path, "alice", "hash", "type", "public-key", "private-key", NOW.toString())
            Repositories.create(DatabaseConfig(path)).use { repositories ->
                block(
                    repositories.deliveryQueueRepository,
                    repositories.failedDeliveryRepository,
                    checkNotNull(repositories.accountRepository.findByUsername("alice")).id,
                )
            }
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val INBOX = "https://remote.example/inbox"
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
        val FAILED_AT: Instant = Instant.parse("2026-11-08T12:00:00Z")
    }
}
