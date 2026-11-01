package net.matsudamper.kmp.activitypub.timeline

import java.time.Duration
import io.lettuce.core.Consumer
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
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer

class ValkeyTimelineStore(uri: String) : TimelineStore {
    private val resources = ClientResources.builder()
        .tracing(LettuceTelemetry.create(OpenTelemetryInitializer.get()).createTracing()).build()
    private val client = RedisClient.create(resources, uri)
    private val connectionDelegate = lazy { client.connect() }
    private val connection: StatefulRedisConnection<String, String> by connectionDelegate
    private val streamConnectionDelegate = lazy { client.connect() }
    private val streamConnection: StatefulRedisConnection<String, String> by streamConnectionDelegate
    private var claimCursor = "0-0"

    override fun built(accountId: AccountId): Boolean = connection.sync().exists("${key(accountId)}:built") > 0

    override fun lock(accountId: AccountId): Boolean =
        connection.sync().set("${key(accountId)}:lock", "1", SetArgs.Builder.nx().px(10000)) == "OK"

    override fun markBuilt(accountId: AccountId) {
        connection.sync().set("${key(accountId)}:built", "1", SetArgs.Builder.ex(TTL))
    }

    override fun unlock(accountId: AccountId) {
        connection.sync().del("${key(accountId)}:lock")
    }

    override fun invalidate(accountId: AccountId) {
        connection.sync().del("${key(accountId)}:built", key(accountId))
    }

    override fun expire(accountId: AccountId) {
        connection.sync().expire(key(accountId), TTL)
        connection.sync().expire("${key(accountId)}:built", TTL)
    }

    override fun distribute(accountIds: List<AccountId>, position: TimelinePosition) {
        val commands = connection.async()
        val oldestReads = accountIds.associateWith { commands.zrangeWithScores(key(it), 0, 0) }
        val oldestByAccount = oldestReads.mapValues { (_, future) ->
            runCatching {
                future.get().firstOrNull()?.let { TimelinePosition(it.score.toLong(), it.value) }
            }
        }
        val failures = oldestByAccount.filterValues { it.isFailure }.keys
        failures.forEach { runCatching { invalidate(it) } }
        check(failures.isEmpty()) { "タイムラインの末尾を取得できなかった" }
        val recipients = accountIds.filter { accountId ->
            val oldest = oldestByAccount.getValue(accountId).getOrThrow()
            oldest == null || position.score > oldest.score || (position.score == oldest.score && position.member >= oldest.member)
        }
        append(recipients, listOf(position))
    }

    override fun appendRebuilt(accountId: AccountId, positions: List<TimelinePosition>) {
        append(listOf(accountId), positions)
    }

    override fun read(accountId: AccountId, after: TimelinePosition?, limit: Int): List<TimelinePosition> {
        val upper = after?.score?.toDouble() ?: Double.POSITIVE_INFINITY
        val boundaryCount = if (after == null) 0L else connection.sync().zcount(key(accountId), Range.create(after.score.toDouble(), after.score.toDouble()))
        val result = connection.sync().zrangeWithScores(
            key(accountId),
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

    private fun append(accountIds: List<AccountId>, positions: List<TimelinePosition>) {
        val commands = connection.async()
        val writes = accountIds.associateWith { account ->
            buildList {
                positions.forEach { position -> add(commands.zadd(key(account), position.score.toDouble(), position.member)) }
                add(commands.zremrangebyrank(key(account), 0, -401))
                add(commands.expire(key(account), TTL))
            }
        }
        val failures = writes.filterValues { futures -> futures.any { runCatching { it.get() }.isFailure } }.keys
        failures.forEach { runCatching { invalidate(it) } }
        check(failures.isEmpty()) { "タイムラインへの配布に失敗した" }
    }

    private fun key(accountId: AccountId): String = "home:{${accountId.value}}"

    private fun StreamMessage<String, String>.toJob(): FanoutJob =
        FanoutJob(id, TimelinePosition(checkNotNull(body["score"]).toLong(), checkNotNull(body["member"])))

    private companion object {
        const val TTL = 604800L
        const val STREAM = "timeline:fanout"
        const val GROUP = "home-timeline"
    }
}
