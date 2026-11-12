package net.matsudamper.kmp.activitypub.timeline

import java.time.Duration
import io.lettuce.core.ClientOptions
import io.lettuce.core.Consumer
import io.lettuce.core.MaintNotificationsConfig
import io.lettuce.core.Range
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.StreamMessage
import io.lettuce.core.XAutoClaimArgs
import io.lettuce.core.XGroupCreateArgs
import io.lettuce.core.XReadArgs
import io.lettuce.core.ZRange
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.resource.ClientResources
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry
import net.matsudamper.kmp.activitypub.repository.TimelineCachePolicy
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer

class ValkeyTimelineStore(uri: String) : TimelineStore {
    private val resources = ClientResources.builder()
        .tracing(LettuceTelemetry.create(OpenTelemetryInitializer.get()).createTracing()).build()
    private val client = RedisClient.create(resources, uri).apply {
        setOptions(ClientOptions.builder().maintNotificationsConfig(MaintNotificationsConfig.disabled()).build())
    }
    private val connectionDelegate = lazy { client.connect() }
    private val connection: StatefulRedisConnection<String, String> by connectionDelegate
    private val streamConnectionDelegate = lazy { client.connect() }
    private val streamConnection: StatefulRedisConnection<String, String> by streamConnectionDelegate
    private var claimCursor = "0-0"

    override fun ready(feed: TimelineFeed): Boolean {
        val state = connection.sync().mget("${key(feed)}:built", "${key(feed)}:lock")
        return state[0].hasValue() && !state[1].hasValue()
    }

    override fun lock(feed: TimelineFeed): Boolean =
        connection.sync().set("${key(feed)}:lock", "1", SetArgs.Builder.nx().px(10000)) == "OK"

    override fun markBuilt(feed: TimelineFeed) {
        connection.sync().set("${key(feed)}:built", "1", SetArgs.Builder.ex(TimelineCachePolicy.BUILT_TTL_SECONDS))
    }

    override fun unlock(feed: TimelineFeed) {
        connection.sync().del("${key(feed)}:lock")
    }

    override fun invalidate(feed: TimelineFeed) {
        connection.sync().del("${key(feed)}:built", key(feed))
    }

    override fun expire(feed: TimelineFeed) {
        connection.sync().expire(key(feed), TimelineCachePolicy.CACHE_TTL_SECONDS)
    }

    override fun distribute(feeds: List<TimelineFeed>, position: TimelinePosition): Int {
        val commands = connection.async()
        val oldestReads = feeds.associateWith { commands.zrangeWithScores(key(it), 0, 0) }
        val oldestByFeed = oldestReads.mapValues { (_, future) ->
            runCatching {
                future.get().firstOrNull()?.let { TimelinePosition(it.score.toLong(), it.value) }
            }
        }
        val failures = oldestByFeed.filterValues { it.isFailure }.keys
        failures.forEach { runCatching { invalidate(it) } }
        check(failures.isEmpty()) { "タイムラインの末尾を取得できなかった" }
        val recipients = feeds.filter { feed ->
            val oldest = oldestByFeed.getValue(feed).getOrThrow()
            oldest == null || position.score > oldest.score || (position.score == oldest.score && position.member >= oldest.member)
        }
        append(recipients, listOf(position), null)
        return recipients.size
    }

    override fun appendRebuilt(feed: TimelineFeed, positions: List<TimelinePosition>) {
        append(listOf(feed), positions, positions.lastOrNull())
    }

    override fun read(feed: TimelineFeed, after: TimelinePosition?, limit: Int): List<TimelinePosition> {
        val upper = after?.score?.toDouble() ?: Double.POSITIVE_INFINITY
        val boundaryCount = if (after == null) 0L else connection.sync().zcount(key(feed), Range.create(after.score.toDouble(), after.score.toDouble()))
        val result = connection.sync().zrangeWithScores(
            key(feed),
            ZRange.byScore(Range.create(Double.NEGATIVE_INFINITY, upper)).rev().limit(0, limit.toLong() + boundaryCount),
        )
        return result.asSequence().map { TimelinePosition(it.score.toLong(), it.value) }
            .filter { after == null || it.score < after.score || (it.score == after.score && it.member < after.member) }
            .take(limit).toList()
    }

    override fun enqueue(position: TimelinePosition) {
        connection.sync().xadd(STREAM, mapOf("member" to position.member, "score" to position.score.toString()))
    }

    override fun initializeGroup() {
        runCatching { streamConnection.sync().xgroupCreate(XReadArgs.StreamOffset.from(STREAM, "0-0"), GROUP, XGroupCreateArgs.Builder.mkstream()) }
            .onFailure { if (it.message?.contains("BUSYGROUP") != true) throw it }
        claimCursor = "0-0"
    }

    override fun jobs(consumer: String, block: Boolean): List<FanoutJob> {
        val owner = Consumer.from(GROUP, consumer)
        val reclaimed = streamConnection.sync().xautoclaim(
            STREAM,
            XAutoClaimArgs.Builder.xautoclaim(owner, Duration.ofMinutes(5), claimCursor).count(100),
        )
        claimCursor = reclaimed.id
        if (reclaimed.messages.isNotEmpty()) return reclaimed.messages.map { it.toJob() }
        val args = XReadArgs.Builder.count(100)
        if (block) args.block(Duration.ofSeconds(1))
        val fresh = streamConnection.sync().xreadgroup(owner, args, XReadArgs.StreamOffset.lastConsumed(STREAM))
        return (reclaimed.messages + fresh).map { it.toJob() }
    }

    override fun acknowledge(id: String) {
        val commands = streamConnection.sync()
        commands.multi()
        try {
            commands.xack(STREAM, GROUP, id)
            commands.xdel(STREAM, id)
            commands.exec()
        } catch (failure: Exception) {
            runCatching { commands.discard() }
            throw failure
        }
    }

    override fun close() {
        if (connectionDelegate.isInitialized()) connection.close()
        if (streamConnectionDelegate.isInitialized()) streamConnection.close()
        client.shutdown()
        resources.shutdown()
    }

    private fun append(feeds: List<TimelineFeed>, positions: List<TimelinePosition>, rebuiltTail: TimelinePosition?) {
        val commands = connection.async()
        val writes = feeds.associateWith { account ->
            buildList {
                if (positions.isEmpty()) {
                    add(commands.del(key(account)))
                } else {
                    positions.forEach { position -> add(commands.zadd(key(account), position.score.toDouble(), position.member)) }
                    if (rebuiltTail != null) {
                        val olderScores = Range.from(Range.Boundary.unbounded<Double>(), Range.Boundary.excluding(rebuiltTail.score.toDouble()))
                        add(commands.zremrangebyscore(key(account), olderScores))
                    }
                    add(commands.zremrangebyrank(key(account), 0, -401))
                    add(commands.expire(key(account), TimelineCachePolicy.CACHE_TTL_SECONDS))
                }
            }
        }
        val failures = writes.filterValues { futures -> futures.any { runCatching { it.get() }.isFailure } }.keys
        failures.forEach { runCatching { invalidate(it) } }
        check(failures.isEmpty()) { "タイムラインへの配布に失敗した" }
    }

    /**
     * 購読先を混ぜて 1 本で持っていた頃の `home:{id}` とは別の名前にする。同じ名前だと、残っていた混ざった中身をメインとして読んでしまう
     */
    private fun key(feed: TimelineFeed): String {
        val source = feed.groupId?.let { "group:${it.value}" } ?: "main"
        return "timeline:{${feed.accountId.value}}:$source"
    }

    private fun StreamMessage<String, String>.toJob(): FanoutJob =
        FanoutJob(id, TimelinePosition(checkNotNull(body["score"]).toLong(), checkNotNull(body["member"])))

    private companion object {
        const val STREAM = "timeline:fanout"
        const val GROUP = "home-timeline"
    }
}
