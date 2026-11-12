package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.FollowingService
import net.matsudamper.kmp.activitypub.logic.FollowingSpan
import net.matsudamper.kmp.activitypub.logic.TimelineInvalidator
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.RemoteNoteReceptionInterest
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import net.matsudamper.kmp.activitypub.timeline.AccountEvents
import net.matsudamper.kmp.activitypub.timeline.FakeTimelineStore
import net.matsudamper.kmp.activitypub.timeline.TimelineService
import net.matsudamper.kmp.activitypub.timeline.TimelineSpan
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.boost.BoostStore.ReceivedRemoteBoost
import net.matsudamper.kotpub.httpsignature.PublicKeyLookup
import net.matsudamper.kotpub.note.NoteVisibility
import net.matsudamper.kotpub.note.ReceivedNote
import net.matsudamper.kotpub.note.ReceivedNoteAttachment
import net.matsudamper.kotpub.webfinger.WebFingerLookup

class RemoteNoteReceptionTest {
    @Test
    fun followAndUndoSpansRecordCanonicalActorAndQueueDecisions() = runBlocking {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { telemetry ->
            val fixture = Fixture(telemetry)
            val profileUrl = "https://remote.example/@remote"
            val request = telemetry.getTracer("test").spanBuilder("setFollowing request").startSpan()
            request.makeCurrent().use {
                withOpenTelemetryContext {
                    fixture.following.setFollowing(fixture.accountId, profileUrl, true)
                    fixture.following.setFollowing(fixture.accountId, ACTOR, true)
                    fixture.following.setFollowing(fixture.accountId, profileUrl, true)
                    fixture.following.setFollowing(fixture.accountId, ACTOR, false)
                    fixture.following.setFollowing(fixture.accountId, ACTOR, false)
                    fixture.repositories.followingRepository.add(fixture.accountId, profileUrl, Instant.EPOCH)
                    fixture.following.setFollowing(fixture.accountId, profileUrl, false)
                    fixture.remote.onFindActor = { fixture.repositories.followingRepository.remove(fixture.accountId, ACTOR) }
                    fixture.following.setFollowing(fixture.accountId, ACTOR, true)
                }
            }
            request.end()
            val services = exporter.finishedSpanItems.filter { it.name == "Following.setFollowing" }
            services.forEach { assertEquals(request.spanContext.spanId, it.parentSpanId) }
            val deliveries = exporter.finishedSpanItems.filter { it.name == "Following.enqueue" }
            assertEquals(5, deliveries.size)
            assertEquals(services.first().spanId, deliveries.first().parentSpanId)
            val first = deliveries.first()
            assertEquals(profileUrl, first.attributes.get(FollowingSpan.REQUESTED_URL))
            assertEquals(ACTOR, first.attributes.get(FollowingSpan.ACTOR_ID))
            assertEquals(true, first.attributes.get(FollowingSpan.ACTOR_ID_DIFFERS))
            assertEquals(listOf(true, false, true, false, false), deliveries.map { it.attributes.get(FollowingSpan.ENQUEUED) })
            assertEquals("canonical_actor_already_followed", deliveries[1].attributes.get(FollowingSpan.SKIP_REASON))
            assertEquals(false, deliveries[2].attributes.get(FollowingSpan.FOLLOWED))
            assertEquals(first.attributes.get(FollowingSpan.FOLLOW_ID), deliveries[2].attributes.get(FollowingSpan.FOLLOW_ID))
            assertTrue(checkNotNull(first.attributes.get(FollowingSpan.FOLLOW_ID)).startsWith("https://local.example/"))
            assertEquals("stored_inbox_missing", deliveries[3].attributes.get(FollowingSpan.SKIP_REASON))
            assertEquals("following_removed", deliveries[4].attributes.get(FollowingSpan.SKIP_REASON))
            assertEquals("already_following", services[1].attributes.get(FollowingSpan.SKIP_REASON))
            assertEquals("following_not_found", services[4].attributes.get(FollowingSpan.SKIP_REASON))
        }
    }

    @Test
    fun createSpansRecordFollowingAndResolvedRecipientCounts() = runBlocking {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { telemetry ->
            val fixture = Fixture(telemetry)
            val repository = fixture.repositories.timelineRepository
            repository.interested = false
            fixture.receiver.save(note(listOf()))
            val localActor = checkNotNull(fixture.directory.resolve("alice")).actorId
            fixture.receiver.save(note(listOf(localActor, localActor, "https://elsewhere.example/users/alice")))
            repository.interested = true
            fixture.receiver.save(note(listOf()))
            val spans = exporter.finishedSpanItems.filter { it.name == "Inbox.Create" }
            assertEquals(listOf(ACTOR, ACTOR, ACTOR), spans.map { it.attributes.get(InboxSpan.ACTOR) })
            assertEquals(listOf(0L, 0L, 1L), spans.map { it.attributes.get(InboxSpan.FOLLOWING_ACCOUNT_COUNT) })
            assertEquals(listOf(0L, 1L, 0L), spans.map { it.attributes.get(InboxSpan.LOCAL_RECIPIENT_COUNT) })
            assertEquals("not_followed_and_not_addressed", spans.first().attributes.get(InboxSpan.DISCARD_REASON))
        }
    }

    @Test
    fun profileUrlFollowUsesDocumentIdAndReceivesCreate() = runBlocking {
        val fixture = Fixture(OpenTelemetry.noop())
        val profileUrl = "https://remote.example/@remote"
        fixture.repositories.remoteActorRepository.save(
            RemoteActor(profileUrl, "$ACTOR/inbox", null, "old-key", RemoteActorProfile("remote", null, profileUrl, null, null, emojis = listOf())).toRecord(),
            Instant.EPOCH,
        )
        val stored = fixture.repositories.timelineRepository
        val repository = object : TimelineRepository by stored {
            override fun receptionInterest(actorUri: String, localRecipients: Set<String>): RemoteNoteReceptionInterest =
                RemoteNoteReceptionInterest(if (fixture.repositories.followingRepository.contains(fixture.accountId, actorUri)) 1 else 0, localRecipients.size.toLong())
        }
        val receiver =
            ReceivedRemoteNotes(repository, fixture.remote, fixture.repositories.remoteActorRepository, fixture.directory, fixture.timeline, Clock.systemUTC(), { actorUri, objectUri ->
                repository.deleteRemote(actorUri, objectUri, Instant.now())
            })
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
        val fixture = Fixture(OpenTelemetry.noop())
        val profileUrl = "https://remote.example/@remote"
        fixture.repositories.remoteActorRepository.save(
            RemoteActor(profileUrl, "$ACTOR/inbox", null, "key", RemoteActorProfile("remote", null, profileUrl, null, null, emojis = listOf())).toRecord(),
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
            val fixture = Fixture(OpenTelemetry.noop())
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
                { actorUri, objectUri -> repository.deleteRemote(actorUri, objectUri, Instant.now()) },
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
        val fixture = Fixture(OpenTelemetry.noop())
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
        val fixture = Fixture(OpenTelemetry.noop())
        fixture.repositories.timelineRepository.interested = false
        fixture.receiver.save(note(listOf("https://elsewhere.example/actor")))
        assertEquals(0, fixture.remote.requests)
        assertTrue(fixture.repositories.remoteActorRepository.savedActors.isEmpty())
        assertTrue(fixture.repositories.timelineRepository.received.isEmpty())
    }

    @Test
    fun interestedCreatesReuseActorsAndFetchOnlyWhenMissing() = runBlocking {
        val fixture = Fixture(OpenTelemetry.noop())
        fixture.receiver.save(note(listOf()))
        fixture.remote.available = false
        fixture.receiver.save(note(listOf()))
        assertEquals(1, fixture.remote.requests)
        assertEquals(2, fixture.repositories.timelineRepository.received.size)
        assertEquals(1, fixture.repositories.remoteActorRepository.savedActors.size)
    }

    @Test
    fun onlyImageAttachmentsAreSavedAsImages() = runBlocking {
        val fixture = Fixture(OpenTelemetry.noop())
        val attachments = listOf(
            ReceivedNoteAttachment(url = "https://remote.example/a.png", mediaType = "image/png", name = "<b>説明</b>"),
            ReceivedNoteAttachment(url = "https://remote.example/b.mp4", mediaType = "video/mp4", name = null),
            ReceivedNoteAttachment(url = "https://remote.example/c", mediaType = null, name = null),
            ReceivedNoteAttachment(url = "https://remote.example/d.webp", mediaType = "image/webp", name = null),
        )
        fixture.receiver.save(note(listOf()).copy(attachments = attachments))
        assertEquals(
            listOf(
                TimelinePostImage.Remote(url = "https://remote.example/a.png", description = "説明"),
                TimelinePostImage.Remote(url = "https://remote.example/d.webp", description = null),
            ),
            fixture.repositories.timelineRepository.received.single().images,
        )
    }

    @Test
    fun addressedLocalAccountsAreResolvedBeforeFetchingActors() = runBlocking {
        val fixture = Fixture(OpenTelemetry.noop())
        fixture.repositories.timelineRepository.interested = false
        fixture.receiver.save(note(listOf(checkNotNull(fixture.directory.resolve("alice")).actorId)))
        assertEquals(1, fixture.remote.requests)
        assertEquals(setOf("alice"), fixture.repositories.timelineRepository.received.single().localRecipients)
    }

    @Test
    fun unfollowUsesStoredInboxWhenRemoteServerIsUnavailable() = runBlocking {
        val fixture = Fixture(OpenTelemetry.noop())
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
        val fixture = Fixture(OpenTelemetry.noop())
        fixture.remote.available = false
        fixture.repositories.followingRepository.add(fixture.accountId, ACTOR, Instant.EPOCH)
        fixture.repositories.followingRepository.recordActivity(fixture.accountId, ACTOR, "follow-1")
        assertTrue(fixture.following.setFollowing(fixture.accountId, ACTOR, false))
        assertFalse(fixture.following.isFollowing(fixture.accountId, ACTOR))
        assertEquals(0, fixture.remote.requests)
        assertTrue(fixture.repositories.deliveryQueueRepository.followBodies.isEmpty())
    }

    @Test
    fun boostsOfFollowedActorStoreBoosterAndAuthorAndSanitizeTheNote() = runBlocking {
        val fixture = Fixture(OpenTelemetry.noop())
        fixture.receiver.saveBoost(boost())
        assertEquals(listOf(BOOSTER, ACTOR), fixture.repositories.remoteActorRepository.savedActors.keys.toList())
        val received = fixture.repositories.timelineRepository.receivedBoosts.single()
        assertEquals("$BOOSTER/announces/1", received.activityUri)
        assertEquals(BOOSTER, received.boosterUri)
        assertEquals(Instant.EPOCH, received.boostedAt)
        assertEquals("<p>本文</p>", received.note.contentHtml)
        assertEquals("public", received.note.visibility)
    }

    @Test
    fun boostsOfUnfollowedActorDoNotFetchActors() = runBlocking {
        val fixture = Fixture(OpenTelemetry.noop())
        fixture.repositories.timelineRepository.interested = false
        fixture.receiver.saveBoost(boost())
        assertEquals(0, fixture.remote.requests)
        assertTrue(fixture.repositories.timelineRepository.receivedBoosts.isEmpty())
    }

    private fun boost(): ReceivedRemoteBoost = ReceivedRemoteBoost(
        activityUri = "$BOOSTER/announces/1",
        actorUri = BOOSTER,
        visibility = NoteVisibility.Public,
        to = listOf(),
        cc = listOf(),
        published = Instant.EPOCH,
        note = note(listOf()),
    )

    private fun note(to: List<String>): ReceivedNote = ReceivedNote(
        "$ACTOR/notes/1", ACTOR, "<p>本文</p><script>bad()</script>", Instant.EPOCH, to, listOf(), null, null, false, NoteVisibility.Public, listOf(), listOf(),
    )

    private class Fixture(openTelemetry: OpenTelemetry) {
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
        val timeline =
            TimelineService(
                repositories.timelineRepository,
                repositories.followingGroupRepository,
                FakeTimelineStore(mutableListOf()),
                AccountEvents(repositories.timelineRepository),
                Clock.systemUTC(),
            )
        val receiver =
            ReceivedRemoteNotes(
                repositories.timelineRepository,
                remote,
                repositories.remoteActorRepository,
                directory,
                timeline,
                Clock.systemUTC(),
                { actorUri, objectUri -> repositories.timelineRepository.deleteRemote(actorUri, objectUri, Instant.now()) },
                openTelemetry,
            )
        val delivery = QueuedFollowDelivery(
            AccountService(repositories.accountRepository),
            directory,
            remote,
            repositories.remoteActorRepository,
            repositories.followingRepository,
            repositories.deliveryQueueRepository,
            Clock.systemUTC(),
            {},
            openTelemetry,
        )
        val following = FollowingService(
            repositories.accountRepository,
            repositories.followingRepository,
            delivery,
            TimelineInvalidator {},
            "local.example",
            Clock.systemUTC(),
            openTelemetry,
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
            val id = if (actorId == BOOSTER) BOOSTER else ACTOR
            return RemoteActor(id, "$id/inbox", null, "key", RemoteActorProfile("remote", null, null, null, null, emojis = listOf()))
        }
        override suspend fun find(keyId: String): PublicKeyLookup = PublicKeyLookup.Unavailable
        override suspend fun refresh(keyId: String): PublicKeyLookup = PublicKeyLookup.Unavailable
        override fun close() = Unit
    }

    private companion object {
        const val ACTOR = "https://remote.example/actor"
        const val BOOSTER = "https://booster.example/actor"
    }
}
