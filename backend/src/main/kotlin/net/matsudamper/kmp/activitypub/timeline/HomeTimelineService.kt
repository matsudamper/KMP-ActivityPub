package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import java.util.Base64
import net.matsudamper.kmp.activitypub.logic.TimelineInvalidator
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.slf4j.LoggerFactory

class HomeTimelineService(
    private val repository: TimelineRepository,
    private val store: TimelineStore,
    private val clock: Clock,
) : TimelineInvalidator {
    private val logger = LoggerFactory.getLogger(HomeTimelineService::class.java)

    fun page(accountId: AccountId, cursor: String?, limit: Int): TimelinePage {
        val after = cursor?.let { TimelineCursor.decode(it) }
        require(cursor == null || after != null) { "cursor が読めない" }
        val pageSize = limit.coerceIn(1, 100)
        repository.touch(accountId, clock.instant())
        val cached = runCatching { cachedPositions(accountId, after, pageSize + 1) }
            .onFailure { logger.warn("Valkey を使えないので DB からタイムラインを読む", it) }.getOrNull()
        val positions = cached ?: repository.page(accountId, after, pageSize + 1)
        val posts = repository.hydrate(accountId, positions.map { it.member }).associateBy { it.position.member }
        val selected = positions.take(pageSize)
        return TimelinePage(
            posts = selected.mapNotNull { posts[it.member] },
            nextCursor = if (positions.size > pageSize) selected.lastOrNull()?.let { TimelineCursor.encode(it) } else null,
        )
    }

    fun enqueue(position: TimelinePosition) {
        runCatching { store.enqueue(position) }.onFailure { logger.warn("タイムライン配布ジョブを積めなかった", it) }
    }

    override fun invalidate(accountId: AccountId) {
        runCatching { store.invalidate(accountId) }.onFailure { logger.warn("タイムラインの組み立て済みフラグを消せなかった", it) }
    }

    private fun cachedPositions(accountId: AccountId, after: TimelinePosition?, limit: Int): List<TimelinePosition>? {
        if (!store.built(accountId)) {
            if (!store.lock(accountId)) return null
            try {
                store.markBuilt(accountId)
                store.appendRebuilt(accountId, repository.rebuild(accountId, 400))
            } catch (failure: Exception) {
                runCatching { store.invalidate(accountId) }
                throw failure
            } finally {
                store.unlock(accountId)
            }
        }
        store.expire(accountId)
        val positions = store.read(accountId, after, limit)
        if (positions.size >= limit) return positions
        val tail = positions.lastOrNull() ?: after
        return positions + repository.page(accountId, tail, limit - positions.size)
    }
}

data class TimelinePage(val posts: List<TimelinePost>, val nextCursor: String?)

object TimelineCursor {
    fun encode(position: TimelinePosition): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("${position.score}:${position.member}".toByteArray())

    fun decode(cursor: String): TimelinePosition? = runCatching {
        val decoded = Base64.getUrlDecoder().decode(cursor).decodeToString()
        val member = decoded.substringAfter(':')
        require(member.matches(Regex("[lr][1-9][0-9]*")))
        TimelinePosition(decoded.substringBefore(':').toLong(), member)
    }.getOrNull()
}
