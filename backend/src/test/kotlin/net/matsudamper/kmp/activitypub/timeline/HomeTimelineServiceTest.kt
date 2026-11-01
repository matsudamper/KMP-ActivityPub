package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import java.time.Instant
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
    fun invalidationDropsRemovedPostsBeforeRebuilding() {
        val repository = FakeTimelineRepository()
        repository.positions.addAll(listOf(TimelinePosition(2, "r1"), TimelinePosition(1, "r2")))
        val service = HomeTimelineService(repository, FakeTimelineStore(repository.events), Clock.systemUTC())
        assertEquals(1, service.page(AccountId(1), null, 1).posts.size)
        repository.positions.clear()
        service.invalidate(AccountId(1))
        val rebuilt = service.page(AccountId(1), null, 1)
        assertTrue(rebuilt.posts.isEmpty())
        assertEquals(null, rebuilt.nextCursor)
    }

    @Test
    fun delayedFanoutOlderThanCacheTailDoesNotHideSqlContinuation() {
        val account = AccountId(1)
        val repository = FakeTimelineRepository()
        val existing = (1..30).map { TimelinePosition(100L - it, "r$it") }
        repository.positions.addAll(existing)
        val prefixRepository = object : TimelineRepository by repository {
            override fun rebuild(accountId: AccountId, limit: Int): List<TimelinePosition> = existing.take(20)
            override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<AccountId> = listOf(account)
        }
        val store = FakeTimelineStore(repository.events)
        val service = HomeTimelineService(prefixRepository, store, Clock.systemUTC())
        val first = service.page(account, null, 7)
        val delayed = TimelinePosition(10, "r31")
        repository.positions.add(delayed)
        val deliveryStore = object : TimelineStore by store {
            override fun jobs(consumer: String, block: Boolean): List<FanoutJob> = listOf(FanoutJob("1-0", delayed))
        }
        TimelineFanoutWorker(prefixRepository, deliveryStore, Clock.systemUTC()).runOnce(false)
        assertEquals(existing.take(20), store.read(account, null, 100))
        val allPages = buildList {
            addAll(first.posts.map { it.position })
            var cursor = first.nextCursor
            while (cursor != null) {
                val page = service.page(account, cursor, 7)
                addAll(page.posts.map { it.position })
                cursor = page.nextCursor
            }
        }
        assertEquals(existing + delayed, allPages)
    }

    @Test
    fun fanoutChecksEachRecipientTailIncludingLexicalTiesButRebuildAddsOlderPositions() {
        val store = FakeTimelineStore(mutableListOf())
        val first = AccountId(1)
        val second = AccountId(2)
        val empty = AccountId(3)
        val newer = TimelinePosition(20, "r9")
        val firstTail = TimelinePosition(10, "r5")
        val secondTail = TimelinePosition(10, "r1")
        val delivery = TimelinePosition(10, "r3")
        store.appendRebuilt(first, listOf(newer, firstTail))
        store.appendRebuilt(second, listOf(secondTail))
        store.distribute(listOf(first, second, empty), delivery)
        assertEquals(listOf(newer, firstTail), store.read(first, null, 100))
        assertEquals(listOf(delivery, secondTail), store.read(second, null, 100))
        assertEquals(listOf(delivery), store.read(empty, null, 100))
        val older = TimelinePosition(9, "r99")
        store.appendRebuilt(first, listOf(older))
        assertEquals(listOf(newer, firstTail, older), store.read(first, null, 100))
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
    private val built = mutableSetOf<AccountId>()
    private val members = mutableMapOf<AccountId, Map<String, TimelinePosition>>()
    private val positionOrder = compareBy<TimelinePosition> { it.score }.thenBy { it.member }

    override fun built(accountId: AccountId): Boolean {
        check(!unavailable)
        return accountId in built
    }

    override fun lock(accountId: AccountId): Boolean {
        events.add("lock")
        return lockAvailable
    }

    override fun markBuilt(accountId: AccountId) {
        events.add("built")
        built.add(accountId)
    }

    override fun unlock(accountId: AccountId) {
        events.add("unlock")
    }
    override fun invalidate(accountId: AccountId) {
        built.remove(accountId)
        members.remove(accountId)
    }
    override fun expire(accountId: AccountId) {
        events.add("expire")
    }
    override fun distribute(accountIds: List<AccountId>, position: TimelinePosition) {
        val oldestByAccount = accountIds.associateWith { members[it].orEmpty().values.minWithOrNull(positionOrder) }
        accountIds.forEach { accountId ->
            val oldest = oldestByAccount[accountId]
            if (oldest == null || positionOrder.compare(position, oldest) >= 0) {
                appendRebuilt(accountId, listOf(position))
            }
        }
    }

    override fun appendRebuilt(accountId: AccountId, positions: List<TimelinePosition>) {
        events.add("zadd")
        members[accountId] = (members[accountId].orEmpty() + positions.associateBy { it.member }).values
            .sortedWith(positionOrder.reversed()).take(400).associateBy { it.member }
    }

    override fun read(accountId: AccountId, after: TimelinePosition?, limit: Int): List<TimelinePosition> = members[accountId].orEmpty().values
        .sortedWith(positionOrder.reversed())
        .filter { after == null || it.score < after.score || (it.score == after.score && it.member < after.member) }.take(limit)

    override fun enqueue(position: TimelinePosition) = Unit
    override fun initializeGroup() = Unit
    override fun jobs(consumer: String, block: Boolean): List<FanoutJob> = listOf()
    override fun acknowledge(id: String) = Unit
    override fun close() = Unit
}
