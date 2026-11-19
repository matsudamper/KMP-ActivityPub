package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.BlockLists
import net.matsudamper.kmp.activitypub.repository.BlockedActor
import net.matsudamper.kmp.activitypub.repository.FollowRecord
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorLookup
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.http.RemoteFetchFailure
import net.matsudamper.kotpub.httpsignature.PublicKeyLookup
import net.matsudamper.kotpub.json.AppJson

class QueuedBlockDeliveryTest {
    private val repositories = FakeRepositories()
    private val alice = repositories.accountRepository.add("alice", "hash", "type")
    private var enqueuedCount = 0
    private val delivery = QueuedBlockDelivery(
        accounts = AccountService(repositories.accountRepository),
        directory = ActorDirectory(
            "local.example",
            object : StoredActorNames {
                override fun find(username: String): String? = if (username == "alice") username else null
                override fun finds(usernames: Set<String>): Map<String, String> = usernames.filter { it == "alice" }.associateWith { it }
            },
        ),
        actors = Remote(),
        storedActors = repositories.remoteActorRepository,
        actorBlocks = repositories.actorBlockRepository,
        followers = repositories.followerRepository,
        blockLists = BlockLists(repositories.serverBlockRepository, repositories.actorBlockRepository),
        clock = Clock.systemUTC(),
        wakeUpQueue = { enqueuedCount++ },
    )

    @Test
    fun blockingActorSendsBlockOnceAndRejectsItsFollow() = runBlocking {
        follow(CAROL, "https://remote.example/follows/carol")
        follow(DAVE, "https://remote.example/follows/dave")
        repositories.actorBlockRepository.add(alice, BlockedActor(actorUri = CAROL, acct = "carol@remote.example"), Instant.EPOCH)

        delivery.blockActor(alice, CAROL)
        delivery.blockActor(alice, CAROL)

        val block = decode(repositories.actorBlockRepository.blockBodies.single())
        assertEquals("Block", block.string("type"))
        assertEquals(ALICE_ACTOR, block.string("actor"))
        assertEquals(CAROL, block.string("object"))
        assertEquals(block.string("id"), repositories.actorBlockRepository.findBlockActivityId(alice, CAROL))

        val rejected = repositories.followerRepository.rejects.single()
        assertEquals(CAROL, rejected.followerActorUri)
        assertEquals("$CAROL/inbox", rejected.inbox)
        val reject = decode(rejected.body)
        assertEquals("Reject", reject.string("type"))
        assertEquals(ALICE_ACTOR, reject.string("actor"))
        val rejectedFollow = reject.getValue("object").jsonObject
        assertEquals("https://remote.example/follows/carol", rejectedFollow.string("id"))
        assertEquals(CAROL, rejectedFollow.string("actor"))
        assertEquals(ALICE_ACTOR, rejectedFollow.string("object"))
        assertEquals(listOf(DAVE), repositories.followerRepository.listFollowRecords("alice").map { it.followerActorUri })
    }

    @Test
    fun blockingServerRejectsOnlyFollowsFromThatServer() = runBlocking {
        follow(CAROL, "https://remote.example/follows/carol")
        follow(ERIN, "https://other.example/follows/erin")
        repositories.serverBlockRepository.add(alice, "remote.example", Instant.EPOCH)

        delivery.rejectBlockedFollowers(alice)

        assertEquals(listOf(CAROL), repositories.followerRepository.rejects.map { it.followerActorUri })
        assertEquals(listOf(ERIN), repositories.followerRepository.listFollowRecords("alice").map { it.followerActorUri })
        assertEquals(listOf(), repositories.actorBlockRepository.blockBodies)
        assertEquals(1, enqueuedCount)
    }

    @Test
    fun undoEmbedsTheSentBlock() {
        val undo = decode(checkNotNull(delivery.undoBlockBody(alice, CAROL, "$ALICE_ACTOR#blocks/1")))
        assertEquals("Undo", undo.string("type"))
        assertEquals(ALICE_ACTOR, undo.string("actor"))
        val block = undo.getValue("object").jsonObject
        assertEquals("Block", block.string("type"))
        assertEquals("$ALICE_ACTOR#blocks/1", block.string("id"))
        assertEquals(CAROL, block.string("object"))
    }

    private fun follow(actorUri: String, followActivityUri: String) {
        repositories.followerRepository.followRecords += "alice" to FollowRecord(actorUri, followActivityUri, "$actorUri/inbox")
    }

    private fun decode(body: String): JsonObject = AppJson.parseToJsonElement(body).jsonObject

    private fun JsonObject.string(key: String): String = getValue(key).jsonPrimitive.content

    private class Remote : RemoteActors {
        override suspend fun findActor(actorId: String): RemoteActorLookup =
            RemoteActorLookup.Found(RemoteActor(actorId, "$actorId/inbox", null, "key", RemoteActorProfile("carol", null, null, null, null, emojis = listOf())))

        override suspend fun find(keyId: String): PublicKeyLookup = PublicKeyLookup.Unavailable(RemoteFetchFailure.NotHttps(keyId))

        override suspend fun refresh(keyId: String): PublicKeyLookup = PublicKeyLookup.Unavailable(RemoteFetchFailure.NotHttps(keyId))

        override fun close() = Unit
    }

    private companion object {
        const val ALICE_ACTOR = "https://local.example/users/alice"
        const val CAROL = "https://remote.example/users/carol"
        const val DAVE = "https://remote.example/users/dave"
        const val ERIN = "https://other.example/users/erin"
    }
}
