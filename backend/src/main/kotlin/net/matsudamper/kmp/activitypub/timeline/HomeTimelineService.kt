package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import java.util.Base64
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import net.matsudamper.kmp.activitypub.logic.TimelineInvalidator
import net.matsudamper.kmp.activitypub.repository.TimelineCachePolicy
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.inOpenTelemetrySpan
import org.slf4j.LoggerFactory

class HomeTimelineService(
    private val repository: TimelineRepository,
    private val store: TimelineStore,
    private val clock: Clock,
    openTelemetry: OpenTelemetry = OpenTelemetryInitializer.get(),
) : TimelineInvalidator {
    private val tracer = openTelemetry.getTracer("activitypub-timeline")
    private val logger = LoggerFactory.getLogger(HomeTimelineService::class.java)

    fun page(accountId: AccountId, cursor: String?, limit: Int): TimelinePage = inOpenTelemetrySpan(tracer, "HomeTimeline.page", false) { span ->
        span.setAttribute(TimelineSpan.CACHE_READ, false)
        span.setAttribute(TimelineSpan.SQL_READ, false)
        span.setAttribute(TimelineSpan.REBUILT, false)
        val after = cursor?.let { TimelineCursor.decode(it) }
        require(cursor == null || after != null) { "cursor が読めない" }
        val pageSize = limit.coerceIn(1, 100)
        repository.touch(accountId, clock.instant(), TimelineCachePolicy.ACTIVITY_UPDATE_INTERVAL_SECONDS)
        val posts = buildList {
            var positionCursor = after
            while (size < pageSize + 1) {
                val remaining = pageSize + 1 - size
                val cached = runCatching { cachedPositions(accountId, positionCursor, remaining) }
                    .onFailure { logger.warn("Valkey を使えないので DB からタイムラインを読む", it) }.getOrNull()
                val positions = if (cached != null) {
                    cached
                } else {
                    span.setAttribute(TimelineSpan.SQL_READ, true)
                    repository.page(accountId, positionCursor, remaining)
                }
                if (positions.isEmpty()) break
                val hydrated = repository.hydrate(accountId, positions.map { it.member }).associateBy { it.position.member }
                addAll(positions.mapNotNull { hydrated[it.member] })
                positionCursor = positions.last()
                if (positions.size < remaining) break
            }
        }
        val selected = posts.take(pageSize)
        TimelinePage(
            posts = selected,
            nextCursor = if (posts.size > pageSize) selected.lastOrNull()?.let { TimelineCursor.encode(it.position) } else null,
        )
    }

    fun enqueue(position: TimelinePosition) {
        runCatching { store.enqueue(position) }.onFailure { logger.warn("タイムライン配布ジョブを積めなかった", it) }
    }

    override fun invalidate(accountId: AccountId) {
        runCatching { store.invalidate(accountId) }.onFailure { logger.warn("タイムラインの組み立て済みフラグを消せなかった", it) }
    }

    private fun cachedPositions(accountId: AccountId, after: TimelinePosition?, limit: Int): List<TimelinePosition>? {
        if (!store.ready(accountId)) {
            if (!store.lock(accountId)) return null
            try {
                store.markBuilt(accountId)
                Span.current().setAttribute(TimelineSpan.REBUILT, true)
                Span.current().setAttribute(TimelineSpan.SQL_READ, true)
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
        Span.current().setAttribute(TimelineSpan.CACHE_READ, true)
        if (positions.size >= limit) return positions
        val tail = positions.lastOrNull() ?: after
        Span.current().setAttribute(TimelineSpan.SQL_READ, true)
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
