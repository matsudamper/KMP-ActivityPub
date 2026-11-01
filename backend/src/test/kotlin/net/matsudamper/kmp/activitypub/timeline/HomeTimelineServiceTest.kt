package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

class HomeTimelineServiceTest {
    @Test
    fun rebuildMarksBuiltBeforeReadingSqlAndAddingMembers() {
        val repository = FakeTimelineRepository()
        repository.positions.add(TimelinePosition(1, "r1"))
        val store = FakeTimelineStore(repository.events)
        val service = HomeTimelineService(repository, store, Clock.systemUTC())
        assertEquals(1, service.page(AccountId(1), null, 10).posts.size)
        assertEquals(listOf("lock", "built", "rebuild", "zadd", "unlock", "expire"), repository.events)
    }

    @Test
    fun equalScoresPageByMemberAndContinueInSqlBeyondFourHundred() {
        val repository = FakeTimelineRepository()
        repository.positions.addAll((1..450).map { TimelinePosition(100, "r$it") })
        val store = FakeTimelineStore(repository.events)
        val service = HomeTimelineService(repository, store, Clock.systemUTC())
        val received = buildList {
            var cursor: String? = null
            do {
                val page = service.page(AccountId(1), cursor, 37)
                addAll(page.posts.map { it.position })
                cursor = page.nextCursor
            } while (cursor != null)
        }
        assertEquals(repository.positions.sortedByDescending { it.member }, received)
        assertTrue(repository.pageRequests.any { it != null })
    }

    @Test
    fun pagesContinueFromRebuiltPrefixWithoutSkippingBusyActors() {
        val repository = FakeTimelineRepository()
        val newer = (1..30).map { TimelinePosition(100L - it, "r$it") }
        val older = (31..50).map { TimelinePosition(-it.toLong(), "r$it") }
        repository.positions.addAll(newer + older)
        val prefixRepository = object : TimelineRepository by repository {
            override fun rebuild(accountId: AccountId, limit: Int): List<TimelinePosition> = newer.take(20)
        }
        val service = HomeTimelineService(prefixRepository, FakeTimelineStore(repository.events), Clock.systemUTC())
        val received = buildList {
            var cursor: String? = null
            do {
                val page = service.page(AccountId(1), cursor, 7)
                addAll(page.posts.map { it.position })
                cursor = page.nextCursor
            } while (cursor != null)
        }
        assertEquals(newer + older, received)
    }

    @Test
    fun unavailableValkeyAndContendedLockUseSqlWithoutBuilding() {
        listOf(false, true).forEach { unavailable ->
            val repository = FakeTimelineRepository()
            repository.positions.add(TimelinePosition(1, "l1"))
            val store = FakeTimelineStore(repository.events)
            store.unavailable = unavailable
            store.lockAvailable = false
            val page = HomeTimelineService(repository, store, Clock.systemUTC()).page(AccountId(1), null, 10)
            assertEquals("l1", page.posts.single().position.member)
            assertTrue("built" !in repository.events)
        }
    }
}

internal class FakeTimelineStore(private val events: MutableList<String>) : TimelineStore {
    var unavailable = false
    var lockAvailable = true
    private var built = false
    private val members = mutableMapOf<String, TimelinePosition>()

    override fun built(accountId: AccountId): Boolean {
        check(!unavailable)
        return built
    }

    override fun lock(accountId: AccountId): Boolean {
        events.add("lock")
        return lockAvailable
    }

    override fun markBuilt(accountId: AccountId) {
        events.add("built")
        built = true
    }

    override fun unlock(accountId: AccountId) {
        events.add("unlock")
    }
    override fun invalidate(accountId: AccountId) {
        built = false
    }
    override fun expire(accountId: AccountId) {
        events.add("expire")
    }
    override fun append(accountIds: List<AccountId>, positions: List<TimelinePosition>) {
        events.add("zadd")
        members.putAll(positions.associateBy { it.member })
    }

    override fun read(accountId: AccountId, after: TimelinePosition?, limit: Int): List<TimelinePosition> = members.values
        .sortedWith(compareByDescending<TimelinePosition> { it.score }.thenByDescending { it.member })
        .filter { after == null || it.score < after.score || (it.score == after.score && it.member < after.member) }.take(limit)

    override fun enqueue(position: TimelinePosition) = Unit
    override fun initializeGroup() = Unit
    override fun jobs(consumer: String, block: Boolean): List<FanoutJob> = listOf()
    override fun acknowledge(id: String) = Unit
    override fun close() = Unit
}
