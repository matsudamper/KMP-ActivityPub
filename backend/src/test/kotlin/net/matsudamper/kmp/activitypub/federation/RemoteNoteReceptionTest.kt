package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.FollowingService
import net.matsudamper.kmp.activitypub.logic.TimelineInvalidator
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import net.matsudamper.kmp.activitypub.timeline.FakeTimelineStore
import net.matsudamper.kmp.activitypub.timeline.HomeTimelineService
import net.matsudamper.kmp.activitypub.timeline.TimelineSpan
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.httpsignature.PublicKeyLookup
import net.matsudamper.kotpub.note.NoteVisibility
import net.matsudamper.kotpub.note.ReceivedNote
import net.matsudamper.kotpub.webfinger.WebFingerLookup

class RemoteNoteReceptionTest {
    @Test
    fun profileUrlFollowUsesDocumentIdAndReceivesCreate() = runBlocking {
        val fixture = Fixture()
        val profileUrl = "https://remote.example/@remote"
        fixture.repositories.remoteActorRepository.save(
            RemoteActor(profileUrl, "$ACTOR/inbox", null, "old-key", RemoteActorProfile("remote", null, profileUrl, null)).toRecord(),
            Instant.EPOCH,
        )
        val stored = fixture.repositories.timelineRepository
        val repository = object : TimelineRepository by stored {
            override fun shouldReceive(actorUri: String, localRecipients: Set<String>): Boolean =
                fixture.repositories.followingRepository.contains(fixture.accountId, actorUri) || localRecipients.isNotEmpty()
        }
        val receiver = ReceivedRemoteNotes(repository, fixture.remote, fixture.repositories.remoteActorRepository, fixture.directory, fixture.timeline, Clock.systemUTC())
        receiver.save(note(listOf()))
        assertTrue(stored.received.isEmpty())
        assertTrue(fixture.following.setFollowing(fixture.accountId, profileUrl, true))
        assertTrue(fixture.following.isFollowing(fixture.accountId, ACTOR))
        assertFalse(fixture.following.isFollowing(fixture.accountId, profileUrl))
        assertEquals(1L, fixture.repositories.followingRepository.count(fixture.accountId))
        assertEquals(null, fixture.repositories.remoteActorRepository.findByUri(profileUrl))
        assertEquals(listOf(ACTOR), fixture.repositories.remoteActorRepository.searchByName("remote", null, 10).map { it.actorUri })
        assertTrue(fixture.repositories.deliveryQueueRepository.followBodies.single().contains("\"object\":\"$ACTOR\""))
        receiver.save(note(listOf()))
        assertEquals(ACTOR, stored.received.single().actorUri)
        assertTrue(fixture.following.setFollowing(fixture.accountId, profileUrl, true))
        assertEquals(1L, fixture.repositories.followingRepository.count(fixture.accountId))
        assertEquals(1, fixture.repositories.deliveryQueueRepository.followBodies.size)
    }

    @Test
    fun urlLookupRemovesLegacyActorAndLegacyFollowingCanBeRemoved() = runBlocking {
        val fixture = Fixture()
        val profileUrl = "https://remote.example/@remote"
        fixture.repositories.remoteActorRepository.save(
            RemoteActor(profileUrl, "$ACTOR/inbox", null, "key", RemoteActorProfile("remote", null, profileUrl, null)).toRecord(),
            Instant.EPOCH,
        )
        fixture.repositories.followingRepository.add(fixture.accountId, profileUrl, Instant.EPOCH)
        val lookup = KotpubRemoteUserLookup(
            object : WebFingerLookup {
                override suspend fun findActorId(acct: String): String? = error("WebFinger は不要")
            },
            fixture.remote,
            fixture.repositories.remoteActorRepository,
        )
        assertEquals(ACTOR, lookup.findByUrl(profileUrl)?.actorUri)
        assertEquals(null, fixture.repositories.remoteActorRepository.findByUri(profileUrl))
        assertEquals(listOf(ACTOR), fixture.repositories.remoteActorRepository.searchByName("remote", null, 10).map { it.actorUri })
        assertTrue(fixture.following.setFollowing(fixture.accountId, profileUrl, false))
        assertEquals(0L, fixture.repositories.followingRepository.count(fixture.accountId))
    }

    @Test
    fun createSpanKeepsRequestParentAndRecordsSavedAndDiscardReasons() = runBlocking {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { telemetry ->
            val fixture = Fixture()
            val stored = fixture.repositories.timelineRepository
            val repository = object : TimelineRepository by stored {
                override fun receive(note: RemoteNoteInput): TimelinePosition? =
                    if (stored.hasRemoteNote(note.objectUri)) null else stored.receive(note)
            }
            val receiver = ReceivedRemoteNotes(
                repository,
                fixture.remote,
                fixture.repositories.remoteActorRepository,
                fixture.directory,
                fixture.timeline,
                Clock.systemUTC(),
                telemetry,
            )
            val request = telemetry.getTracer("test").spanBuilder("inbox request").startSpan()
            request.makeCurrent().use {
                withOpenTelemetryContext {
                    stored.interested = false
                    receiver.save(note(listOf()))
                    stored.interested = true
                    receiver.save(note(listOf()))
                    receiver.save(note(listOf()))
                }
            }
            request.end()
            val creates = exporter.finishedSpanItems.filter { it.name == "Inbox.Create" }
            assertEquals(3, creates.size)
            creates.forEach { assertEquals(request.spanContext.spanId, it.parentSpanId) }
            assertEquals(listOf(false, true, false), creates.map { it.attributes.get(InboxSpan.SAVED) })
            assertEquals("not_followed_and_not_addressed", creates[0].attributes.get(InboxSpan.DISCARD_REASON))
            assertEquals("saved", creates[1].attributes.get(InboxSpan.OUTCOME))
            assertEquals("r1", creates[1].attributes.get(TimelineSpan.MEMBER))
            assertEquals("duplicate", creates[2].attributes.get(InboxSpan.DISCARD_REASON))
        }
    }

    @Test
    fun unfollowWhileResolvingActorPreventsFollowEnqueue() = runBlocking {
        val fixture = Fixture()
        fixture.remote.onFindActor = {
            assertTrue(fixture.following.setFollowing(fixture.accountId, ACTOR, false))
        }
        assertTrue(fixture.following.setFollowing(fixture.accountId, ACTOR, true))
        assertFalse(fixture.following.isFollowing(fixture.accountId, ACTOR))
        assertEquals(null, fixture.repositories.followingRepository.followId(fixture.accountId, ACTOR))
        assertTrue(fixture.repositories.deliveryQueueRepository.followBodies.isEmpty())
    }

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
        var onFindActor: suspend () -> Unit = {}
        override suspend fun findActor(actorId: String): RemoteActor? {
            requests++
            onFindActor()
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
