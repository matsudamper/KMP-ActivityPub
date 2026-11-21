package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId

class ActorBlockRepositoryTest {
    @Test
    fun actorsAreListedNewestFirstPerAccountAndReblockUpdatesAcct() {
        withAliceAndBob { repositories, alice, bob ->
            val blocks = repositories.actorBlockRepository
            blocks.add(alice, BlockedActor("https://old.example/users/a", "a@old.example"), Instant.ofEpochSecond(1))
            blocks.add(alice, BlockedActor("https://new.example/users/b", "b@new.example"), Instant.ofEpochSecond(2))
            blocks.add(alice, BlockedActor("https://old.example/users/a", "renamed@old.example"), Instant.ofEpochSecond(3))
            blocks.add(bob, BlockedActor("https://bob.example/users/c", "c@bob.example"), Instant.ofEpochSecond(1))
            assertEquals(
                listOf(
                    BlockedActor("https://new.example/users/b", "b@new.example"),
                    BlockedActor("https://old.example/users/a", "renamed@old.example"),
                ),
                blocks.list(alice),
            )
            assertEquals(listOf(BlockedActor("https://bob.example/users/c", "c@bob.example")), blocks.list(bob))
        }
    }

    @Test
    fun removeDeletesOnlyThatAccountsActor() {
        withAliceAndBob { repositories, alice, bob ->
            val blocks = repositories.actorBlockRepository
            val actor = BlockedActor("https://remote.example/users/a", "a@remote.example")
            blocks.add(alice, actor, Instant.EPOCH)
            blocks.add(bob, actor, Instant.EPOCH)
            blocks.remove(alice, actor.actorUri, Instant.EPOCH) { error("Block を送っていない") }
            blocks.remove(alice, "https://missing.example/users/x", Instant.EPOCH) { error("ブロックしていない") }
            assertEquals(listOf(), blocks.list(alice))
            assertEquals(listOf(actor), blocks.list(bob))
        }
    }

    @Test
    fun blockIsRecordedOnceAndUndoneToTheSameInboxAfterItWasSent() {
        withAliceAndBob { repositories, alice, _ ->
            val blocks = repositories.actorBlockRepository
            val queue = repositories.deliveryQueueRepository
            blocks.add(alice, ACTOR, Instant.EPOCH)
            assertTrue(blocks.recordBlockActivity(alice, ACTOR.actorUri, "block-1", "https://remote.example/inbox", "{\"id\":\"block-1\"}", Instant.EPOCH))
            assertFalse(blocks.recordBlockActivity(alice, ACTOR.actorUri, "block-2", "https://remote.example/inbox", "{\"id\":\"block-2\"}", Instant.EPOCH))
            assertEquals("block-1", blocks.findBlockActivityId(alice, ACTOR.actorUri))

            val block = queue.claim(Instant.EPOCH, 10, setOf()).single()
            assertEquals("{\"id\":\"block-1\"}", block.body)
            queue.markDelivered(block.id, Instant.EPOCH)

            assertTrue(blocks.remove(alice, ACTOR.actorUri, Instant.EPOCH) { "undo:$it" })
            val undo = queue.claim(Instant.EPOCH, 10, setOf()).single()
            assertEquals("https://remote.example/inbox", undo.inbox)
            assertEquals("undo:block-1", undo.body)
            assertEquals(listOf(), blocks.list(alice))
        }
    }

    @Test
    fun unblockBeforeTheBlockIsSentDropsTheBlockWithoutUndo() {
        withAliceAndBob { repositories, alice, _ ->
            val blocks = repositories.actorBlockRepository
            blocks.add(alice, ACTOR, Instant.EPOCH)
            blocks.recordBlockActivity(alice, ACTOR.actorUri, "block-1", "https://remote.example/inbox", "{\"id\":\"block-1\"}", Instant.EPOCH)

            assertFalse(blocks.remove(alice, ACTOR.actorUri, Instant.EPOCH) { "undo:$it" })

            assertEquals(listOf(), repositories.deliveryQueueRepository.claim(Instant.EPOCH, 10, setOf()))
        }
    }

    @Test
    fun unblockWhileTheBlockIsRetryingDropsTheBlockAndUndoes() {
        withAliceAndBob { repositories, alice, _ ->
            val blocks = repositories.actorBlockRepository
            val queue = repositories.deliveryQueueRepository
            blocks.add(alice, ACTOR, Instant.EPOCH)
            blocks.recordBlockActivity(alice, ACTOR.actorUri, "block-1", "https://remote.example/inbox", "{\"id\":\"block-1\"}", Instant.EPOCH)
            queue.scheduleRetry(queue.claim(Instant.EPOCH, 10, setOf()).single().id, Instant.EPOCH.plusSeconds(60), "失敗")

            assertTrue(blocks.remove(alice, ACTOR.actorUri, Instant.EPOCH) { "undo:$it" })

            assertEquals(listOf("undo:block-1"), queue.claim(Instant.EPOCH.plusSeconds(120), 10, setOf()).map { it.body })
        }
    }

    @Test
    fun unblockWhileTheBlockIsBeingSentDoesNotRetryTheBlock() {
        withAliceAndBob { repositories, alice, _ ->
            val blocks = repositories.actorBlockRepository
            val queue = repositories.deliveryQueueRepository
            blocks.add(alice, ACTOR, Instant.EPOCH)
            blocks.recordBlockActivity(alice, ACTOR.actorUri, "block-1", "https://remote.example/inbox", "{\"id\":\"block-1\"}", Instant.EPOCH)
            val delivering = queue.claim(Instant.EPOCH, 10, setOf()).single()

            assertTrue(blocks.remove(alice, ACTOR.actorUri, Instant.EPOCH) { "undo:$it" })
            queue.scheduleRetry(delivering.id, Instant.EPOCH.plusSeconds(60), "失敗")

            assertEquals(listOf("undo:block-1"), queue.claim(Instant.EPOCH.plusSeconds(120), 10, setOf()).map { it.body })
        }
    }

    private fun withAliceAndBob(block: (Repositories, AccountId, AccountId) -> Unit) {
        val directory = createTempDirectory("actor-block-test")
        val path = directory.resolve("test.db")
        try {
            TestSchema.applyTo(path)
            listOf("alice", "bob").forEach { username ->
                Accounts.insert(
                    databasePath = path,
                    username = username,
                    passwordHash = "hash",
                    passwordHashType = "hash-type",
                    publicKeyPem = "public-key",
                    privateKeyPem = "private-key",
                    createdAt = Instant.EPOCH.toString(),
                )
            }
            Repositories.create(DatabaseConfig(path = path)).use { repositories ->
                val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
                val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
                block(repositories, alice, bob)
            }
        } finally {
            directory.toFile().listFiles().orEmpty().forEach { it.toPath().deleteIfExists() }
            directory.deleteIfExists()
        }
    }

    private companion object {
        val ACTOR = BlockedActor("https://remote.example/users/a", "a@remote.example")
    }
}
