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
    )

    @Test
    fun followsFromBlockedActorOrServerAreRefused() {
        repositories.actorBlockRepository.add(alice, BlockedActor(actorUri = CAROL, acct = "carol@remote.example"), Instant.EPOCH)
        repositories.serverBlockRepository.add(alice, "blocked.example", Instant.EPOCH)

        assertTrue(store.refuses("alice", CAROL))
        assertTrue(store.refuses("alice", "https://blocked.example/users/dave"))
        assertFalse(store.refuses("alice", "https://remote.example/users/erin"))
    }

    @Test
    fun rejectEnqueuesRejectToTheFollowerInbox() {
        val follower = RemoteActor(CAROL, "$CAROL/inbox", null, "key", RemoteActorProfile("carol", null, null, null, null, emojis = listOf()))

        assertTrue(store.reject("alice", follower, "https://remote.example/follows/1", Instant.EPOCH, "{\"type\":\"Reject\"}"))

        val rejected = repositories.followerRepository.rejects.single()
        assertEquals(CAROL, rejected.followerActorUri)
        assertEquals("$CAROL/inbox", rejected.inbox)
        assertEquals("{\"type\":\"Reject\"}", rejected.body)
        assertEquals(1, enqueuedCount)
    }

    private companion object {
        const val CAROL = "https://remote.example/users/carol"
    }
}
