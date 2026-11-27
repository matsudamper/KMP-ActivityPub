package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.logic.BlockLists
import net.matsudamper.kmp.activitypub.logic.NotificationRecorder
import net.matsudamper.kmp.activitypub.repository.BlockedActor
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile

class AccountFollowerStoreTest {
    private val repositories = FakeRepositories()
    private val alice = repositories.accountRepository.add("alice", "hash", "type")
    private var enqueuedCount = 0
    private val store = AccountFollowerStore(
        followerRepository = repositories.followerRepository,
        blocks = InboxBlocks(
            repositories.accountRepository,
            repositories.noteRepository,
            BlockLists(repositories.serverBlockRepository, repositories.actorBlockRepository),
        ),
        notifications = NotificationRecorder { },
        onEnqueued = { enqueuedCount++ },
        maxDeliveryTargetsPerHost = { LIMIT },
    )

    @Test
    fun followsFromBlockedActorOrServerAreRefused() {
        repositories.actorBlockRepository.add(alice, BlockedActor(actorUri = CAROL, acct = "carol@remote.example"), Instant.EPOCH)
        repositories.serverBlockRepository.add(alice, "blocked.example", Instant.EPOCH)

        assertTrue(store.refuses("alice", actor(CAROL)))
        assertTrue(store.refuses("alice", actor("https://blocked.example/users/dave")))
        assertFalse(store.refuses("alice", actor("https://remote.example/users/erin")))
    }

    @Test
    fun followsThatWouldExceedTheDeliveryTargetLimitOfTheHostAreRefused() {
        recordSpamFollowersUpToLimit()

        assertTrue(store.refuses("alice", actor("https://spam.example/users/new")))
        assertFalse(store.refuses("alice", actor("https://other.example/users/new")))
    }

    @Test
    fun followersSharingAnInboxDoNotCountTowardsTheLimit() {
        repeat(LIMIT * 2) { index ->
            repositories.followerRepository.followerActors += actor("https://social.example/users/$index", sharedInbox = "https://social.example/inbox").toRecord()
        }

        assertFalse(store.refuses("alice", actor("https://social.example/users/new", sharedInbox = "https://social.example/inbox")))
    }

    @Test
    fun recordedFollowerMovingItsOwnInboxIsNotRefusedAtTheLimit() {
        recordSpamFollowersUpToLimit()

        assertFalse(store.refuses("alice", actor("https://spam.example/users/0")))
        assertFalse(store.refuses("alice", actor("https://spam.example/users/0", inbox = "https://spam.example/users/0/moved-inbox")))
    }

    @Test
    fun recordedFollowerWhoseTargetDoesNotGrowIsNotRefusedEvenOverTheLimit() {
        repeat(LIMIT + 2) { index ->
            repositories.followerRepository.followerActors += actor("https://spam.example/users/$index").toRecord()
        }

        assertFalse(store.refuses("alice", actor("https://spam.example/users/0")))
        assertTrue(store.refuses("alice", actor("https://spam.example/users/new")))
    }

    @Test
    fun theLimitIsReadOnEveryCheck() {
        var limit = LIMIT
        val store = AccountFollowerStore(
            followerRepository = repositories.followerRepository,
            blocks = InboxBlocks(
                repositories.accountRepository,
                repositories.noteRepository,
                BlockLists(repositories.serverBlockRepository, repositories.actorBlockRepository),
            ),
            notifications = NotificationRecorder { },
            onEnqueued = { },
            maxDeliveryTargetsPerHost = { limit },
        )
        recordSpamFollowersUpToLimit()
        assertTrue(store.refuses("alice", actor("https://spam.example/users/new")))

        limit = LIMIT + 1

        assertFalse(store.refuses("alice", actor("https://spam.example/users/new")))
    }

    @Test
    fun rejectEnqueuesRejectToTheFollowerInbox() {
        val follower = RemoteActor(CAROL, "$CAROL/inbox", null, "key", RemoteActorProfile("carol", null, null, null, null, emojis = listOf()), alsoKnownAs = listOf())

        assertTrue(store.reject("alice", follower, "https://remote.example/follows/1", Instant.EPOCH, "{\"type\":\"Reject\"}"))

        val rejected = repositories.followerRepository.rejects.single()
        assertEquals(CAROL, rejected.followerActorUri)
        assertEquals("$CAROL/inbox", rejected.inbox)
        assertEquals("{\"type\":\"Reject\"}", rejected.body)
        assertEquals(1, enqueuedCount)
    }

    private fun recordSpamFollowersUpToLimit() {
        repeat(LIMIT) { index ->
            repositories.followerRepository.followerActors += actor("https://spam.example/users/$index").toRecord()
        }
    }

    private fun actor(
        actorId: String,
        inbox: String = "$actorId/inbox",
        sharedInbox: String? = null,
    ): RemoteActor = RemoteActor(actorId, inbox, sharedInbox, "key", RemoteActorProfile(null, null, null, null, null, emojis = listOf()), alsoKnownAs = listOf())

    private fun RemoteActor.toRecord(): RemoteActorRecord = RemoteActorRecord(
        actorUri = actorId,
        inbox = inbox,
        sharedInbox = sharedInbox,
        publicKeyPem = publicKeyPem,
        profile = RemoteActorProfileRecord(
            preferredUsername = null,
            displayName = null,
            profileUrl = null,
            iconUrl = null,
            headerUrl = null,
            emojis = listOf(),
            summary = null,
            fields = listOf(),
        ),
    )

    private companion object {
        const val CAROL = "https://remote.example/users/carol"
        const val LIMIT = 3
    }
}
