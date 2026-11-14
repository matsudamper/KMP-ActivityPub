package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.TimelineCachePolicy
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelineRecipient
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

class TimelineServiceTest {
    @Test
    fun boostMemberCanBeUsedAsCursor() {
        val position = TimelinePosition(100, "b12")
        assertEquals(position, TimelineCursor.decode(TimelineCursor.encode(position)))
    }

    @Test
    fun deletedCachedPositionsAreReplacedWithOlderPostsIncludingSqlContinuation() {
        listOf(10, 400).forEach { deletedCount ->
            val account = AccountId(1)
            val repository = FakeTimelineRepository()
            val positions = (1..430).map { TimelinePosition(1000L - it, "r$it") }
            repository.positions.addAll(positions)
            val service = TimelineService(repository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, FakeTimelineStore(repository.events), AccountEvents(repository), Clock.systemUTC())
            service.page(account, null, null, 10)
            repository.positions.removeAll(positions.take(deletedCount).toSet())
            val first = service.page(account, null, null, 10)
            assertEquals(positions.drop(deletedCount).take(10), first.posts.map { it.position })
            assertEquals(first.posts.last().position, first.nextCursor?.let { TimelineCursor.decode(it) })
            val second = service.page(account, null, first.nextCursor, 10)
            assertEquals(positions.drop(deletedCount + 10).take(10), second.posts.map { it.position })
            if (deletedCount == 400) assertTrue(repository.pageRequests.any { it != null })
        }
    }

    @Test
    fun postsFromBlockedServerAreSkippedAndPageIsFilledWithLaterPosts() {
        val account = AccountId(1)
        val repository = FakeTimelineRepository()
        val positions = (1..30).map { TimelinePosition(100L - it, "r$it") }
        repository.positions.addAll(positions)
        val blockedMembers = positions.take(15).filter { it.member.drop(1).toInt() % 2 == 1 }.map { it.member }.toSet()
        val remoteRepository = object : TimelineRepository by repository {
            override fun hydrate(accountId: AccountId, members: List<String>) = repository.hydrate(accountId, members).map { post ->
                val host = if (post.position.member in blockedMembers) "blocked.example" else "other.example"
                post.copy(actorUri = "https://$host/users/${post.position.member}")
            }
        }
        val serverBlocks = FakeRepositories().serverBlockRepository
        serverBlocks.add(account, "blocked.example", Instant.EPOCH)
        val service = TimelineService(remoteRepository, FakeRepositories().followingGroupRepository, serverBlocks, FakeTimelineStore(repository.events), AccountEvents(remoteRepository), Clock.systemUTC())

        val first = service.page(account, null, null, 10)
        val second = service.page(account, null, first.nextCursor, 10)

        val visible = positions.filterNot { it.member in blockedMembers }
        assertEquals(visible.take(10), first.posts.map { it.position })
        assertEquals(visible.drop(10).take(10), second.posts.map { it.position })
        assertEquals(positions.take(10), service.page(AccountId(2), null, null, 10).posts.map { it.position })
    }

    @Test
    fun deletedPositionsAtEndDoNotProduceAnEmptyNextPage() {
        val account = AccountId(1)
        val repository = FakeTimelineRepository()
        val positions = (1..15).map { TimelinePosition(100L - it, "r$it") }
        repository.positions.addAll(positions)
        val service = TimelineService(repository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, FakeTimelineStore(repository.events), AccountEvents(repository), Clock.systemUTC())
        service.page(account, null, null, 10)
        repository.positions.removeAll(positions.drop(5).toSet())
        val page = service.page(account, null, null, 10)
        assertEquals(positions.take(5), page.posts.map { it.position })
        assertEquals(null, page.nextCursor)
        repository.positions.clear()
        val empty = service.page(account, null, null, 10)
        assertTrue(empty.posts.isEmpty())
        assertEquals(null, empty.nextCursor)
    }

    @Test
    fun readingDuringRebuildUsesOnlySqlEvenAfterMarkBuilt() {
        val account = AccountId(1)
        val repository = FakeTimelineRepository()
        val newest = TimelinePosition(3, "r3")
        val next = TimelinePosition(2, "r2")
        val old = TimelinePosition(1, "r1")
        repository.positions.addAll(listOf(newest, next, old))
        val store = FakeTimelineStore(repository.events)
        store.distribute(listOf(account).map { TimelineFeed(it, null) }, old)
        assertTrue(store.lock(TimelineFeed(account, null)))
        store.markBuilt(TimelineFeed(account, null))
        val service = TimelineService(repository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, store, AccountEvents(repository), Clock.systemUTC())
        val page = service.page(account, null, null, 1)
        assertEquals(listOf(newest), page.posts.map { it.position })
        assertEquals(newest, page.nextCursor?.let { TimelineCursor.decode(it) })
        assertEquals(0, repository.events.count { it == "rebuild" })
        assertEquals(listOf(old), store.read(TimelineFeed(account, null), null, 10))
        store.appendRebuilt(TimelineFeed(account, null), repository.rebuild(account, null, 400))
        store.unlock(TimelineFeed(account, null))
        assertTrue(store.ready(TimelineFeed(account, null)))
        assertEquals(listOf(newest, next, old), service.page(account, null, null, 10).posts.map { it.position })
    }

    @Test
    fun repeatedReadsDoNotExtendBuiltExpirationAndRebuildAtTwentyFourHours() {
        val account = AccountId(1)
        val repository = FakeTimelineRepository()
        repository.positions.add(TimelinePosition(1, "r1"))
        val store = FakeTimelineStore(repository.events)
        val service = TimelineService(repository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, store, AccountEvents(repository), Clock.systemUTC())
        service.page(account, null, null, 10)
        (1..23).forEach { hour ->
            store.now = Instant.EPOCH.plusSeconds(hour * 3600L)
            service.page(account, null, null, 10)
        }
        store.now = Instant.EPOCH.plusSeconds(24 * 3600L - 1)
        service.page(account, null, null, 10)
        assertTrue(store.ready(TimelineFeed(account, null)))
        assertEquals(1, repository.events.count { it == "rebuild" })
        store.now = Instant.EPOCH.plusSeconds(24 * 3600L)
        assertTrue(!store.ready(TimelineFeed(account, null)))
        service.page(account, null, null, 10)
        assertEquals(2, repository.events.count { it == "rebuild" })
        assertEquals(listOf("lock", "built", "rebuild", "zadd", "unlock", "expire"), repository.events.takeLast(6))
        assertTrue(store.ready(TimelineFeed(account, null)))
    }

    @Test
    fun failedFanoutEnqueueIsRecoveredFromDatabaseAfterBuiltExpiration() {
        val account = AccountId(1)
        val repository = FakeTimelineRepository()
        val existing = TimelinePosition(1, "r1")
        repository.positions.add(existing)
        val store = FakeTimelineStore(repository.events)
        val failingQueueStore = object : TimelineStore by store {
            override fun enqueue(position: TimelinePosition) {
                error("XADD 失敗")
            }
        }
        val service = TimelineService(repository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, failingQueueStore, AccountEvents(repository), Clock.systemUTC())
        assertEquals(listOf(existing), service.page(account, null, null, 10).posts.map { it.position })
        val missed = TimelinePosition(2, "r2")
        repository.positions.add(missed)
        service.enqueue(missed)
        store.now = Instant.EPOCH.plusSeconds(23 * 3600L)
        assertEquals(listOf(existing), service.page(account, null, null, 10).posts.map { it.position })
        store.now = Instant.EPOCH.plusSeconds(24 * 3600L)
        assertEquals(listOf(missed, existing), service.page(account, null, null, 10).posts.map { it.position })
        assertEquals(2, repository.events.count { it == "rebuild" })
    }

    @Test
    fun fanoutIncludesAccountsActiveSevenDaysAndThirtyMinutesAgo() {
        val now = Instant.parse("2026-11-01T12:00:00Z")
        val recentlyActive = AccountId(1)
        val boundary = AccountId(2)
        val expired = AccountId(3)
        val lastActive = mapOf(
            recentlyActive to now.minusSeconds(7 * 24 * 60 * 60L + 30 * 60),
            boundary to now.minusSeconds(7 * 24 * 60 * 60L + 60 * 60),
            expired to now.minusSeconds(7 * 24 * 60 * 60L + 60 * 60 + 1),
        )
        val repository = FakeTimelineRepository()
        val recipientsRepository = object : TimelineRepository by repository {
            override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<TimelineRecipient> =
                lastActive.filterValues { it >= activeSince }.keys.map { TimelineRecipient(it, null) }
        }
        val store = FakeTimelineStore(repository.events)
        val position = TimelinePosition(now.toEpochMilli(), "r1")
        val deliveryStore = object : TimelineStore by store {
            override fun jobs(consumer: String, block: Boolean): List<FanoutJob> = listOf(FanoutJob("1-0", position))
        }
        TimelineFanoutWorker(recipientsRepository, deliveryStore, AccountEvents(recipientsRepository), Clock.fixed(now, ZoneOffset.UTC)).runOnce(false)
        assertEquals(listOf(position), store.read(TimelineFeed(recentlyActive, null), null, 10))
        assertEquals(listOf(position), store.read(TimelineFeed(boundary, null), null, 10))
        assertTrue(store.read(TimelineFeed(expired, null), null, 10).isEmpty())
    }

    @Test
    fun rebuildMarksBuiltBeforeReadingSqlAndAddingMembers() {
        val repository = FakeTimelineRepository()
        repository.positions.add(TimelinePosition(1, "r1"))
        val store = FakeTimelineStore(repository.events)
        val service = TimelineService(repository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, store, AccountEvents(repository), Clock.systemUTC())
        assertEquals(1, service.page(AccountId(1), null, null, 10).posts.size)
        assertEquals(listOf("lock", "built", "rebuild", "zadd", "unlock", "expire"), repository.events)
    }

    @Test
    fun equalScoresPageByMemberAndContinueInSqlBeyondFourHundred() {
        val repository = FakeTimelineRepository()
        repository.positions.addAll((1..450).map { TimelinePosition(100, "r$it") })
        val store = FakeTimelineStore(repository.events)
        val service = TimelineService(repository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, store, AccountEvents(repository), Clock.systemUTC())
        val received = buildList {
            var cursor: String? = null
            do {
                val page = service.page(AccountId(1), null, cursor, 37)
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
            override fun rebuild(accountId: AccountId, groupId: FollowingGroupId?, limit: Int): List<TimelinePosition> = newer.take(20)
        }
        val service = TimelineService(prefixRepository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, FakeTimelineStore(repository.events), AccountEvents(prefixRepository), Clock.systemUTC())
        val received = buildList {
            var cursor: String? = null
            do {
                val page = service.page(AccountId(1), null, cursor, 7)
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
        val service = TimelineService(repository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, FakeTimelineStore(repository.events), AccountEvents(repository), Clock.systemUTC())
        assertEquals(1, service.page(AccountId(1), null, null, 1).posts.size)
        repository.positions.clear()
        service.invalidate(AccountId(1))
        val rebuilt = service.page(AccountId(1), null, null, 1)
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
            override fun rebuild(accountId: AccountId, groupId: FollowingGroupId?, limit: Int): List<TimelinePosition> = existing.take(20)
            override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<TimelineRecipient> = listOf(TimelineRecipient(account, null))
        }
        val store = FakeTimelineStore(repository.events)
        val service = TimelineService(prefixRepository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, store, AccountEvents(prefixRepository), Clock.systemUTC())
        val first = service.page(account, null, null, 7)
        val delayed = TimelinePosition(10, "r31")
        repository.positions.add(delayed)
        val deliveryStore = object : TimelineStore by store {
            override fun jobs(consumer: String, block: Boolean): List<FanoutJob> = listOf(FanoutJob("1-0", delayed))
        }
        TimelineFanoutWorker(prefixRepository, deliveryStore, AccountEvents(prefixRepository), Clock.systemUTC()).runOnce(false)
        assertEquals(existing.take(20), store.read(TimelineFeed(account, null), null, 100))
        val allPages = buildList {
            addAll(first.posts.map { it.position })
            var cursor = first.nextCursor
            while (cursor != null) {
                val page = service.page(account, null, cursor, 7)
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
        store.appendRebuilt(TimelineFeed(first, null), listOf(newer, firstTail))
        store.appendRebuilt(TimelineFeed(second, null), listOf(secondTail))
        store.distribute(listOf(first, second, empty).map { TimelineFeed(it, null) }, delivery)
        assertEquals(listOf(newer, firstTail), store.read(TimelineFeed(first, null), null, 100))
        assertEquals(listOf(delivery, secondTail), store.read(TimelineFeed(second, null), null, 100))
        assertEquals(listOf(delivery), store.read(TimelineFeed(empty, null), null, 100))
        val older = TimelinePosition(9, "r99")
        store.appendRebuilt(TimelineFeed(first, null), listOf(older))
        assertEquals(listOf(newer, firstTail, older), store.read(TimelineFeed(first, null), null, 100))
    }

    @Test
    fun rebuildingDropsOldFanoutBeforeOrDuringRebuildAndSqlReturnsEveryPage() {
        listOf(false, true).forEach { duringRebuild ->
            val account = AccountId(1)
            val repository = FakeTimelineRepository()
            val existing = (1..30).map { TimelinePosition(100L - it, "r$it") }
            val delayed = TimelinePosition(10, "r31")
            repository.positions.addAll(existing + delayed)
            val prefixRepository = object : TimelineRepository by repository {
                override fun rebuild(accountId: AccountId, groupId: FollowingGroupId?, limit: Int): List<TimelinePosition> = existing.take(20)
            }
            val store = FakeTimelineStore(repository.events)
            val rebuildingStore = object : TimelineStore by store {
                override fun markBuilt(feed: TimelineFeed) {
                    store.markBuilt(feed)
                    if (duringRebuild) store.distribute(listOf(feed), delayed)
                }
            }
            val service = TimelineService(prefixRepository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, rebuildingStore, AccountEvents(prefixRepository), Clock.systemUTC())
            store.appendRebuilt(TimelineFeed(account, null), existing.take(20))
            service.invalidate(account)
            if (!duringRebuild) store.distribute(listOf(account).map { TimelineFeed(it, null) }, delayed)
            val allPages = buildList {
                var cursor: String? = null
                do {
                    val page = service.page(account, null, cursor, 7)
                    addAll(page.posts.map { it.position })
                    cursor = page.nextCursor
                } while (cursor != null)
            }
            assertEquals(existing.take(20), store.read(TimelineFeed(account, null), null, 100))
            assertEquals(existing + delayed, allPages)
        }
    }

    @Test
    fun emptyRebuildClearsFanoutAndEqualScoreMembersRemain() {
        val account = AccountId(1)
        val store = FakeTimelineStore(mutableListOf())
        val tail = TimelinePosition(10, "r5")
        val sameScore = TimelinePosition(10, "r1")
        val newer = TimelinePosition(20, "r10")
        store.distribute(listOf(account).map { TimelineFeed(it, null) }, TimelinePosition(9, "r9"))
        store.distribute(listOf(account).map { TimelineFeed(it, null) }, sameScore)
        store.distribute(listOf(account).map { TimelineFeed(it, null) }, newer)
        store.appendRebuilt(TimelineFeed(account, null), listOf(tail))
        assertEquals(listOf(newer, tail, sameScore), store.read(TimelineFeed(account, null), null, 100))
        store.appendRebuilt(TimelineFeed(account, null), listOf())
        assertTrue(store.read(TimelineFeed(account, null), null, 100).isEmpty())
    }

    @Test
    fun unavailableValkeyAndContendedLockUseSqlWithoutBuilding() {
        listOf(false, true).forEach { unavailable ->
            val repository = FakeTimelineRepository()
            repository.positions.add(TimelinePosition(1, "l1"))
            val store = FakeTimelineStore(repository.events)
            store.unavailable = unavailable
            store.lockAvailable = false
            val page = TimelineService(repository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, store, AccountEvents(repository), Clock.systemUTC()).page(AccountId(1), null, null, 10)
            assertEquals("l1", page.posts.single().position.member)
            assertTrue("built" !in repository.events)
        }
    }

    @Test
    fun eachFollowingGroupIsCachedSeparatelyAndInvalidatedTogether() {
        val account = AccountId(1)
        val groups = FakeRepositories().followingGroupRepository
        val group = groups.create(account, "グループ", Instant.EPOCH)
        val homePosition = TimelinePosition(2, "r2")
        val groupPosition = TimelinePosition(1, "r1")
        val repository = FakeTimelineRepository()
        repository.positions.addAll(listOf(homePosition, groupPosition))
        val groupedRepository = object : TimelineRepository by repository {
            override fun rebuild(accountId: AccountId, groupId: FollowingGroupId?, limit: Int): List<TimelinePosition> =
                listOf(if (groupId == null) homePosition else groupPosition)

            override fun page(accountId: AccountId, groupId: FollowingGroupId?, after: TimelinePosition?, limit: Int): List<TimelinePosition> = listOf()
        }
        val store = FakeTimelineStore(repository.events)
        val service = TimelineService(groupedRepository, groups, FakeRepositories().serverBlockRepository, store, AccountEvents(groupedRepository), Clock.systemUTC())

        assertEquals(listOf(homePosition), service.page(account, null, null, 10).posts.map { it.position })
        assertEquals(listOf(groupPosition), service.page(account, group.id, null, 10).posts.map { it.position })
        assertEquals(listOf(homePosition), store.read(TimelineFeed(account, null), null, 10))
        assertEquals(listOf(groupPosition), store.read(TimelineFeed(account, group.id), null, 10))

        service.invalidate(account)
        assertTrue(!store.ready(TimelineFeed(account, null)))
        assertTrue(!store.ready(TimelineFeed(account, group.id)))
    }

    @Test
    fun deletedMembersAreStreamedOnlyToAccountsThatHaveThem() = runBlocking {
        val recipient = AccountId(1)
        val other = AccountId(2)
        val repository = FakeTimelineRepository()
        val recipientsRepository = object : TimelineRepository by repository {
            override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<TimelineRecipient> =
                if (member == "b1" && after == null) listOf(TimelineRecipient(recipient, null)) else listOf()
        }
        val events = AccountEvents(recipientsRepository)
        val service = TimelineService(recipientsRepository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, FakeTimelineStore(repository.events), events, Clock.systemUTC())
        val recipientEvent = async(start = CoroutineStart.UNDISPATCHED) { events.events(recipient).first() }
        val otherEvent = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(100) { events.events(other).first() } }

        service.publishDeleted(listOf("r1", "b1"))

        assertEquals(AccountEvent.PostsDeleted(deletedMembers = listOf("b1"), boostUndoneMembers = listOf()), recipientEvent.await())
        assertEquals(null, otherEvent.await())
    }

    @Test
    fun undoneBoostsAreStreamedApartFromDeletedPosts() = runBlocking {
        val recipient = AccountId(1)
        val repository = FakeTimelineRepository()
        val recipientsRepository = object : TimelineRepository by repository {
            override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<TimelineRecipient> =
                if (after == null) listOf(TimelineRecipient(recipient, null)) else listOf()
        }
        val events = AccountEvents(recipientsRepository)
        val service = TimelineService(recipientsRepository, FakeRepositories().followingGroupRepository, FakeRepositories().serverBlockRepository, FakeTimelineStore(repository.events), events, Clock.systemUTC())
        val recipientEvent = async(start = CoroutineStart.UNDISPATCHED) { events.events(recipient).first() }

        service.publishBoostUndone(listOf("b1"))

        assertEquals(AccountEvent.PostsDeleted(deletedMembers = listOf(), boostUndoneMembers = listOf("b1")), recipientEvent.await())
    }
}

internal class FakeTimelineStore(private val events: MutableList<String>) : TimelineStore {
    var unavailable = false
    var lockAvailable = true
    var now: Instant = Instant.EPOCH
    private val locked = mutableSetOf<TimelineFeed>()
    private val builtUntil = mutableMapOf<TimelineFeed, Instant>()
    private val members = mutableMapOf<TimelineFeed, Map<String, TimelinePosition>>()
    private val positionOrder = compareBy<TimelinePosition> { it.score }.thenBy { it.member }

    override fun ready(feed: TimelineFeed): Boolean {
        check(!unavailable)
        return builtUntil[feed]?.isAfter(now) == true && feed !in locked
    }

    override fun lock(feed: TimelineFeed): Boolean {
        events.add("lock")
        return lockAvailable && locked.add(feed)
    }

    override fun markBuilt(feed: TimelineFeed) {
        events.add("built")
        builtUntil[feed] = now.plusSeconds(TimelineCachePolicy.BUILT_TTL_SECONDS)
    }

    override fun unlock(feed: TimelineFeed) {
        events.add("unlock")
        locked.remove(feed)
    }
    override fun invalidate(feed: TimelineFeed) {
        builtUntil.remove(feed)
        members.remove(feed)
    }
    override fun expire(feed: TimelineFeed) {
        events.add("expire")
    }
    override fun distribute(feeds: List<TimelineFeed>, position: TimelinePosition): Int {
        val oldestByFeed = feeds.associateWith { members[it].orEmpty().values.minWithOrNull(positionOrder) }
        val recipients = feeds.filter { feed ->
            val oldest = oldestByFeed[feed]
            oldest == null || positionOrder.compare(position, oldest) >= 0
        }
        recipients.forEach { append(it, listOf(position)) }
        return recipients.size
    }

    override fun appendRebuilt(feed: TimelineFeed, positions: List<TimelinePosition>) {
        val tail = positions.lastOrNull()
        if (tail == null) {
            members.remove(feed)
        } else {
            append(feed, positions)
            members[feed] = members[feed].orEmpty().filterValues { it.score >= tail.score }
        }
    }

    override fun read(feed: TimelineFeed, after: TimelinePosition?, limit: Int): List<TimelinePosition> = members[feed].orEmpty().values
        .sortedWith(positionOrder.reversed())
        .filter { after == null || it.score < after.score || (it.score == after.score && it.member < after.member) }.take(limit)

    override fun enqueue(position: TimelinePosition) = Unit
    override fun initializeGroup() = Unit
    override fun jobs(consumer: String, block: Boolean): List<FanoutJob> = listOf()
    override fun acknowledge(id: String) = Unit
    override fun close() = Unit

    private fun append(feed: TimelineFeed, positions: List<TimelinePosition>) {
        events.add("zadd")
        members[feed] = (members[feed].orEmpty() + positions.associateBy { it.member }).values
            .sortedWith(positionOrder.reversed()).take(400).associateBy { it.member }
    }
}
