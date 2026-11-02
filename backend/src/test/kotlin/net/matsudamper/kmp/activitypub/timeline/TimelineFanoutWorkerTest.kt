package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

class TimelineFanoutWorkerTest {
    @Test
    fun deletedGroupIsRecreatedAndRemainingJobsAreDistributedOnNextIteration() {
        val account = AccountId(1)
        val repository = object : TimelineRepository by FakeTimelineRepository() {
            override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<AccountId> = listOf(account)
        }
        val cache = FakeTimelineStore(mutableListOf())
        val store = RecoverableGroupStore(cache)
        val worker = TimelineFanoutWorker(repository, store, Clock.systemUTC())
        store.initializeGroup()
        assertEquals(0, worker.runOnce(false))
        val position = TimelinePosition(1, "r1")
        store.groupExists = false
        store.remainingJobs = listOf(FanoutJob("1-0", position))
        assertEquals(1, worker.runOnce(false))
        assertEquals(2, store.initializations)
        assertEquals(listOf(position), cache.read(account, null, 10))
        assertEquals(listOf("1-0"), store.acknowledged)
        assertEquals(0, worker.runOnce(false))
        assertEquals(2, store.initializations)
    }

    @Test
    fun otherReadFailuresArePropagatedWithoutRecreatingGroup() {
        val failure = IllegalStateException("接続失敗")
        val store = object : TimelineStore by FakeTimelineStore(mutableListOf()) {
            override fun jobs(consumer: String, block: Boolean): List<FanoutJob> = throw failure
            override fun initializeGroup() = error("再作成してはいけない")
        }
        val worker = TimelineFanoutWorker(FakeTimelineRepository(), store, Clock.systemUTC())
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
