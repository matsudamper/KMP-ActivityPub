package net.matsudamper.kmp.activitypub.timeline

import io.lettuce.core.Limit
import io.lettuce.core.Range
import io.lettuce.core.RedisClient
import io.lettuce.core.ScoredValue
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.resource.ClientResources
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer

/**
 * score を全て 0 にして member の辞書順で並べる。
 * 時刻を score にすると、同じ時刻の投稿がページの境目に来たときに続きの位置を決められない
 */
class ValkeyHomeTimelineStore(
    uri: String,
) : HomeTimelineStore {
    private val clientResources: ClientResources =
        ClientResources
            .builder()
            .tracing(LettuceTelemetry.create(OpenTelemetryInitializer.get()).createTracing())
            .build()
    private val client: RedisClient = RedisClient.create(clientResources, uri)
    private val connection: StatefulRedisConnection<String, String> = client.connect()

    override fun add(
        accountId: AccountId,
        position: NotePosition,
        maxSize: Int,
    ) {
        addAll(accountId = accountId, positions = listOf(position), maxSize = maxSize)
    }

    override fun addAll(
        accountId: AccountId,
        positions: List<NotePosition>,
        maxSize: Int,
    ) {
        if (positions.isEmpty()) return

        val key = keyOf(accountId)
        val commands = connection.sync()
        commands.zadd(key, *positions.map { ScoredValue.just(SCORE, TimelineCursor.encode(it)) }.toTypedArray())
        commands.zremrangebyrank(key, 0, -(maxSize.toLong() + 1))
    }

    override fun list(
        accountId: AccountId,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition>? {
        val key = keyOf(accountId)
        val commands = connection.sync()
        if (commands.exists(key) == 0L) return null

        val upper =
            if (after == null) {
                Range.Boundary.unbounded<String>()
            } else {
                Range.Boundary.excluding(TimelineCursor.encode(after))
            }
        return commands
            .zrevrangebylex(key, Range.from(Range.Boundary.unbounded(), upper), Limit.create(0, limit.toLong()))
            .mapNotNull { TimelineCursor.decode(it) }
    }

    override fun close() {
        connection.close()
        client.shutdown()
        clientResources.shutdown()
    }

    private fun keyOf(accountId: AccountId): String = "home-timeline:${accountId.value}"

    private companion object {
        const val SCORE = 0.0
    }
}
