package net.matsudamper.kmp.activitypub.session

import kotlin.time.Duration
import io.lettuce.core.RedisClient
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection

/**
 * 送信元ごとに 1 つのハッシュを持ち、フィールドをユーザー名、値を失敗回数にする
 */
class ValkeyLoginFailureStore(
    uri: String,
) : LoginFailureStore {
    private val client: RedisClient = RedisClient.create(uri)
    private val connection: StatefulRedisConnection<String, String> = client.connect()

    override fun totalCount(clientIp: String): Long =
        connection.sync().hvals(keyOf(clientIp)).sumOf { it.toLong() }

    override fun increment(
        clientIp: String,
        username: String,
        ttl: Duration,
    ) {
        connection.sync().eval<Long>(
            INCREMENT_WITH_TTL_SCRIPT,
            ScriptOutputType.INTEGER,
            arrayOf(keyOf(clientIp)),
            username,
            ttl.inWholeSeconds.toString(),
        )
    }

    override fun extend(
        clientIp: String,
        ttl: Duration,
    ) {
        connection.sync().expire(keyOf(clientIp), ttl.inWholeSeconds)
    }

    override fun clear(
        clientIp: String,
        username: String,
    ) {
        connection.sync().hdel(keyOf(clientIp), username)
    }

    override fun close() {
        connection.close()
        client.shutdown()
    }

    private fun keyOf(clientIp: String): String = "login-failure:$clientIp"

    private companion object {
        /**
         * HINCRBY と EXPIRE の間で落ちると期限の無いキーが残り、締め出しが解けなくなるので 1 回で行う
         */
        const val INCREMENT_WITH_TTL_SCRIPT = """
            local count = redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
            redis.call('EXPIRE', KEYS[1], ARGV[2])
            return count
        """
    }
}
