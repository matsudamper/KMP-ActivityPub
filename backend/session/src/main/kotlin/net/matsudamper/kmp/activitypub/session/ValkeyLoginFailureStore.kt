package net.matsudamper.kmp.activitypub.session

import kotlin.time.Duration
import io.lettuce.core.RedisClient
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.resource.ClientResources
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer

/**
 * 送信元ごとに 1 つのハッシュを持ち、フィールドをユーザー名、値を失敗回数にする
 */
class ValkeyLoginFailureStore(
    uri: String,
) : LoginFailureStore {
    private val clientResources: ClientResources =
        ClientResources
            .builder()
            .tracing(LettuceTelemetry.create(OpenTelemetryInitializer.get()).createTracing())
            .build()
    private val client: RedisClient = RedisClient.create(clientResources, uri)
    private val connection: StatefulRedisConnection<String, String> = client.connect()

    override fun reserve(
        clientIp: String,
        username: String,
        maxFailures: Int,
        ttl: Duration,
    ): Boolean =
        connection.sync().eval<Long>(
            RESERVE_SCRIPT,
            ScriptOutputType.INTEGER,
            arrayOf(keyOf(clientIp)),
            username,
            ttl.inWholeSeconds.toString(),
            maxFailures.toString(),
        ) == 1L

    override fun clear(
        clientIp: String,
        username: String,
    ) {
        connection.sync().hdel(keyOf(clientIp), username)
    }

    override fun close() {
        connection.close()
        client.shutdown()
        clientResources.shutdown()
    }

    private fun keyOf(clientIp: String): String = "login-failure:$clientIp"

    private companion object {
        /**
         * 合計の判定と加算の間に他のリクエストが入ると上限を超えて照合できるので、1 回で行う
         */
        const val RESERVE_SCRIPT = """
            local total = 0
            for _, count in ipairs(redis.call('HVALS', KEYS[1])) do
                total = total + tonumber(count)
            end
            if total >= tonumber(ARGV[3]) then
                redis.call('EXPIRE', KEYS[1], ARGV[2])
                return 0
            end
            redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return 1
        """
    }
}
