package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import java.time.Instant
import java.util.Base64
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import net.matsudamper.kmp.activitypub.logic.BlockLists
import net.matsudamper.kmp.activitypub.logic.TimelineInvalidator
import net.matsudamper.kmp.activitypub.repository.FollowingGroupRepository
import net.matsudamper.kmp.activitypub.repository.TimelineCachePolicy
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.inOpenTelemetrySpan
import org.slf4j.LoggerFactory

class TimelineService(
    private val repository: TimelineRepository,
    private val groups: FollowingGroupRepository,
    private val blockLists: BlockLists,
    private val store: TimelineStore,
    private val events: AccountEvents,
    private val clock: Clock,
    openTelemetry: OpenTelemetry = OpenTelemetryInitializer.get(),
) : TimelineInvalidator {
    private val tracer = openTelemetry.getTracer("activitypub-timeline")
    private val logger = LoggerFactory.getLogger(TimelineService::class.java)

    /**
     * @param groupId 読む購読先。null ならメイン
     */
    fun page(accountId: AccountId, groupId: FollowingGroupId?, cursor: String?, limit: Int): TimelinePage = inOpenTelemetrySpan(tracer, "HomeTimeline.page", false) { span ->
        val feed = TimelineFeed(accountId, groupId)
        span.setAttribute(TimelineSpan.CACHE_READ, false)
        span.setAttribute(TimelineSpan.SQL_READ, false)
        span.setAttribute(TimelineSpan.REBUILT, false)
        val after = cursor?.let { TimelineCursor.decode(it) }
        require(cursor == null || after != null) { "cursor が読めない" }
        val pageSize = limit.coerceIn(1, 100)
        repository.touch(accountId, clock.instant(), TimelineCachePolicy.ACTIVITY_UPDATE_INTERVAL_SECONDS)
        val blockList = blockLists.of(accountId)
        val posts = buildList {
            var positionCursor = after
            while (size < pageSize + 1) {
                val remaining = pageSize + 1 - size
                val cached = runCatching { cachedPositions(feed, positionCursor, remaining) }
                    .onFailure { logger.warn("Valkey を使えないので DB からタイムラインを読む", it) }.getOrNull()
                val positions = if (cached != null) {
                    cached
                } else {
                    span.setAttribute(TimelineSpan.SQL_READ, true)
                    repository.page(accountId, groupId, positionCursor, remaining)
                }
                if (positions.isEmpty()) break
                val hydrated = repository.hydrate(accountId, positions.map { it.member }).associateBy { it.position.member }
                addAll(positions.mapNotNull { hydrated[it.member] }.filterNot(blockList::hides))
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

    /**
     * 個別投稿画面で [viewer] が開ける投稿。開ける範囲は [TimelineRepository.viewablePost]
     *
     * @param member [TimelinePosition.member] の値
     * @return [member] が読めないか、開けない投稿なら null
     */
    fun viewablePost(viewer: AccountId, member: String): TimelinePost? {
        if (!member.matches(TimelineCursor.MEMBER_PATTERN)) return null
        return repository.viewablePost(viewer, member)
    }

    /**
     * @param member [TimelinePosition.member] の値
     * @return [viewablePost] で開けない投稿なら null
     */
    fun contentHtml(viewer: AccountId, member: String): String? {
        if (!member.matches(TimelineCursor.MEMBER_PATTERN)) return null
        return repository.contentHtml(viewer, member)
    }

    fun enqueue(position: TimelinePosition) {
        runCatching { store.enqueue(position) }.onFailure { logger.warn("タイムライン配布ジョブを積めなかった", it) }
    }

    /**
     * 投稿者が投稿を消して削除済みになった行を、それを載せているアカウントへ知らせる
     *
     * @param members 削除済みになったタイムラインの member
     */
    fun publishDeleted(members: List<String>) {
        publishToRecipients(members) { recipients, member -> events.publishDeletion(recipients, listOf(member)) }
    }

    /**
     * ブーストした相手がブーストを取り消した行を、それを載せているアカウントへ知らせる
     *
     * @param members 取り消されたブーストのタイムラインの member
     */
    fun publishBoostUndone(members: List<String>) {
        publishToRecipients(members) { recipients, member -> events.publishBoostUndone(recipients, listOf(member)) }
    }

    override fun invalidate(accountId: AccountId) {
        val feeds = listOf(TimelineFeed(accountId, null)) + groups.list(accountId).map { TimelineFeed(accountId, it.id) }
        feeds.forEach { feed ->
            runCatching { store.invalidate(feed) }.onFailure { logger.warn("タイムラインの組み立て済みフラグを消せなかった", it) }
        }
    }

    private fun publishToRecipients(members: List<String>, publish: (recipients: List<AccountId>, member: String) -> Unit) {
        members.forEach { member ->
            var after: AccountId? = null
            do {
                // 配布と違い、しばらく開いていなかったアカウントも今つながっていれば知らせる
                val recipients = repository.recipients(member, Instant.EPOCH, after, 1000).map { it.accountId }
                publish(recipients, member)
                after = recipients.lastOrNull()
            } while (recipients.size == 1000)
        }
    }

    private fun cachedPositions(feed: TimelineFeed, after: TimelinePosition?, limit: Int): List<TimelinePosition>? {
        if (!store.ready(feed)) {
            if (!store.lock(feed)) return null
            try {
                store.markBuilt(feed)
                Span.current().setAttribute(TimelineSpan.REBUILT, true)
                Span.current().setAttribute(TimelineSpan.SQL_READ, true)
                store.appendRebuilt(feed, repository.rebuild(feed.accountId, feed.groupId, 400))
            } catch (failure: Exception) {
                runCatching { store.invalidate(feed) }
                throw failure
            } finally {
                store.unlock(feed)
            }
        }
        store.expire(feed)
        val positions = store.read(feed, after, limit)
        Span.current().setAttribute(TimelineSpan.CACHE_READ, true)
        if (positions.size >= limit) return positions
        val tail = positions.lastOrNull() ?: after
        Span.current().setAttribute(TimelineSpan.SQL_READ, true)
        return positions + repository.page(feed.accountId, feed.groupId, tail, limit - positions.size)
    }
}

data class TimelinePage(val posts: List<TimelinePost>, val nextCursor: String?)

object TimelineCursor {
    internal val MEMBER_PATTERN = Regex("[lrba][1-9][0-9]*")

    fun encode(position: TimelinePosition): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("${position.score}:${position.member}".toByteArray())

    fun decode(cursor: String): TimelinePosition? = runCatching {
        val decoded = Base64.getUrlDecoder().decode(cursor).decodeToString()
        val member = decoded.substringAfter(':')
        require(member.matches(MEMBER_PATTERN))
        TimelinePosition(decoded.substringBefore(':').toLong(), member)
    }.getOrNull()
}
