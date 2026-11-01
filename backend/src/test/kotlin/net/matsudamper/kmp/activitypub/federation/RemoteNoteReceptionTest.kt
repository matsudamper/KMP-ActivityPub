package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.FollowingService
import net.matsudamper.kmp.activitypub.logic.TimelineInvalidator
import net.matsudamper.kmp.activitypub.timeline.FakeTimelineStore
import net.matsudamper.kmp.activitypub.timeline.HomeTimelineService
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.httpsignature.PublicKeyLookup
import net.matsudamper.kotpub.note.NoteVisibility
import net.matsudamper.kotpub.note.ReceivedNote

class RemoteNoteReceptionTest {
    @Test
    fun unrelatedCreatesDoNotFetchOrWriteActors() = runBlocking {
        val fixture = Fixture()
        fixture.repositories.timelineRepository.interested = false
        fixture.receiver.save(note(listOf("https://elsewhere.example/actor")))
        assertEquals(0, fixture.remote.requests)
        assertTrue(fixture.repositories.remoteActorRepository.savedActors.isEmpty())
        assertTrue(fixture.repositories.timelineRepository.received.isEmpty())
    }

    @Test
    fun interestedCreatesReuseActorsAndFetchOnlyWhenMissing() = runBlocking {
        val fixture = Fixture()
        fixture.receiver.save(note(listOf()))
        fixture.remote.available = false
        fixture.receiver.save(note(listOf()))
        assertEquals(1, fixture.remote.requests)
        assertEquals(2, fixture.repositories.timelineRepository.received.size)
        assertEquals(1, fixture.repositories.remoteActorRepository.savedActors.size)
    }

    @Test
    fun addressedLocalAccountsAreResolvedBeforeFetchingActors() = runBlocking {
        val fixture = Fixture()
        fixture.repositories.timelineRepository.interested = false
        fixture.receiver.save(note(listOf(checkNotNull(fixture.directory.resolve("alice")).actorId)))
        assertEquals(1, fixture.remote.requests)
        assertEquals(setOf("alice"), fixture.repositories.timelineRepository.received.single().localRecipients)
    }

    @Test
    fun unfollowUsesStoredInboxWhenRemoteServerIsUnavailable() = runBlocking {
        val fixture = Fixture()
        assertTrue(fixture.following.setFollowing(fixture.accountId, ACTOR, true))
        fixture.remote.available = false
        assertTrue(fixture.following.setFollowing(fixture.accountId, ACTOR, false))
        assertFalse(fixture.following.isFollowing(fixture.accountId, ACTOR))
        assertEquals(1, fixture.remote.requests)
        assertEquals(2, fixture.repositories.deliveryQueueRepository.followBodies.size)
        assertTrue(fixture.repositories.deliveryQueueRepository.followBodies.last().contains("\"Undo\""))
    }

    @Test
    fun missingInboxDoesNotPreventLocalUnfollow() = runBlocking {
        val fixture = Fixture()
        fixture.remote.available = false
        fixture.repositories.followingRepository.add(fixture.accountId, ACTOR, Instant.EPOCH)
        fixture.repositories.followingRepository.recordActivity(fixture.accountId, ACTOR, "follow-1")
        assertTrue(fixture.following.setFollowing(fixture.accountId, ACTOR, false))
        assertFalse(fixture.following.isFollowing(fixture.accountId, ACTOR))
        assertEquals(0, fixture.remote.requests)
        assertTrue(fixture.repositories.deliveryQueueRepository.followBodies.isEmpty())
    }

    private fun note(to: List<String>): ReceivedNote = ReceivedNote(
        "$ACTOR/notes/1", ACTOR, "<p>本文</p><script>bad()</script>", Instant.EPOCH, to, listOf(), null, null, false, NoteVisibility.Public,
    )

    private class Fixture {
        val repositories = FakeRepositories()
        val accountId = repositories.accountRepository.add("alice", "hash", "type")
        val remote = Remote()
        val directory = ActorDirectory(
            "local.example",
            object : StoredActorNames {
                override fun find(username: String): String? = if (username == "alice") username else null
                override fun finds(usernames: Set<String>): Map<String, String> = usernames.filter { it == "alice" }.associateWith { it }
            },
        )
        val timeline = HomeTimelineService(repositories.timelineRepository, FakeTimelineStore(mutableListOf()), Clock.systemUTC())
        val receiver = ReceivedRemoteNotes(repositories.timelineRepository, remote, repositories.remoteActorRepository, directory, timeline, Clock.systemUTC())
        val delivery = QueuedFollowDelivery(
            AccountService(repositories.accountRepository),
            directory,
            remote,
            repositories.remoteActorRepository,
            repositories.followingRepository,
            repositories.deliveryQueueRepository,
            Clock.systemUTC(),
            {},
        )
        val following = FollowingService(
            repositories.accountRepository,
            repositories.followingRepository,
            delivery,
            TimelineInvalidator {},
            "local.example",
            Clock.systemUTC(),
        )
    }

    private class Remote : RemoteActors {
        var requests = 0
        var available = true
        override suspend fun findActor(actorId: String): RemoteActor? {
            requests++
            check(available) { "相手サーバーに接続できない" }
            return RemoteActor(ACTOR, "$ACTOR/inbox", null, "key", RemoteActorProfile("remote", null, null, null))
        }
        override suspend fun find(keyId: String): PublicKeyLookup = PublicKeyLookup.Unavailable
        override suspend fun refresh(keyId: String): PublicKeyLookup = PublicKeyLookup.Unavailable
        override fun close() = Unit
    }

    private companion object {
        const val ACTOR = "https://remote.example/actor"
    }
}
