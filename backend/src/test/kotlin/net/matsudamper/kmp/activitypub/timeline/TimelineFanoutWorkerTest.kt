package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.repository.TimelineRecipient

class TimelineFanoutWorkerTest {
    @Test
    fun deletedGroupIsRecreatedAndRemainingJobsAreDistributedOnNextIteration() {
        val account = AccountId(1)
        val repository = object : TimelineRepository by FakeTimelineRepository() {
            override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<TimelineRecipient> = listOf(TimelineRecipient(account, null))
        }
        val cache = FakeTimelineStore(mutableListOf())
        val store = RecoverableGroupStore(cache)
        val worker = TimelineFanoutWorker(repository, store, AccountEvents(repository), Clock.systemUTC())
        store.initializeGroup()
        assertEquals(0, worker.runOnce(false))
        val position = TimelinePosition(1, "r1")
        store.groupExists = false
        store.remainingJobs = listOf(FanoutJob("1-0", position))
        assertEquals(1, worker.runOnce(false))
        assertEquals(2, store.initializations)
        assertEquals(listOf(position), cache.read(TimelineFeed(account, null), null, 10))
        assertEquals(listOf("1-0"), store.acknowledged)
        assertEquals(0, worker.runOnce(false))
        assertEquals(2, store.initializations)
    }

    @Test
    fun distributedPostIsStreamedOnlyToRecipients() = runBlocking {
        val recipient = AccountId(1)
        val other = AccountId(2)
        val position = TimelinePosition(1, "r1")
        val stored = FakeTimelineRepository()
        stored.positions.add(position)
        val repository = object : TimelineRepository by stored {
            override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<TimelineRecipient> = listOf(TimelineRecipient(recipient, null))
        }
        val arrivals = AccountEvents(repository)
        val store = object : TimelineStore by FakeTimelineStore(mutableListOf()) {
            override fun jobs(consumer: String, block: Boolean): List<FanoutJob> = listOf(FanoutJob("1-0", position))
            override fun acknowledge(id: String) = Unit
        }
        val recipientPosts = async(start = CoroutineStart.UNDISPATCHED) { arrivals.events(recipient).first() }
        val otherPosts = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(100) { arrivals.events(other).first() } }

        TimelineFanoutWorker(repository, store, arrivals, Clock.systemUTC()).runOnce(false)

        assertEquals(position, assertIs<AccountEvent.PostArrived>(recipientPosts.await()).post.position)
        assertNull(otherPosts.await())
    }

    @Test
    fun otherReadFailuresArePropagatedWithoutRecreatingGroup() {
        val failure = IllegalStateException("接続失敗")
        val store = object : TimelineStore by FakeTimelineStore(mutableListOf()) {
            override fun jobs(consumer: String, block: Boolean): List<FanoutJob> = throw failure
            override fun initializeGroup() = error("再作成してはいけない")
        }
        val worker = TimelineFanoutWorker(FakeTimelineRepository(), store, AccountEvents(FakeTimelineRepository()), Clock.systemUTC())
        assertSame(failure, assertFailsWith<IllegalStateException> { worker.runOnce(false) })
    }

    private class RecoverableGroupStore(cache: TimelineStore) : TimelineStore by cache {
        var groupExists = false
        var initializations = 0
        var remainingJobs = listOf<FanoutJob>()
        val acknowledged = mutableListOf<String>()

        override fun initializeGroup() {
            initializations++
            groupExists = true
        }

        override fun jobs(consumer: String, block: Boolean): List<FanoutJob> {
            check(groupExists) { "NOGROUP No such consumer group" }
            return remainingJobs
        }

        override fun acknowledge(id: String) {
            acknowledged.add(id)
            remainingJobs = remainingJobs.filter { it.id != id }
        }
    }
}
