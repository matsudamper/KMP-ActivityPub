package net.matsudamper.kmp.activitypub.session

import kotlin.time.Duration
import io.lettuce.core.RedisClient
import io.lettuce.core.ScriptOutputType
import io.lettuce.core.api.StatefulRedisConnection

class ValkeyLoginFailureStore(
    uri: String,
) : LoginFailureStore {
    private val client: RedisClient = RedisClient.create(uri)
    private val connection: StatefulRedisConnection<String, String> = client.connect()

    override fun count(clientIp: String): Long = connection.sync().get(keyOf(clientIp))?.toLong() ?: 0

    override fun increment(
        clientIp: String,
        ttl: Duration,
    ) {
        connection.sync().eval<Long>(
            INCREMENT_WITH_TTL_SCRIPT,
            ScriptOutputType.INTEGER,
            arrayOf(keyOf(clientIp)),
            ttl.inWholeSeconds.toString(),
        )
    }

    override fun clear(clientIp: String) {
        connection.sync().del(keyOf(clientIp))
    }

    override fun close() {
        connection.close()
        client.shutdown()
    }

    private fun keyOf(clientIp: String): String = "login-failure:$clientIp"

    private companion object {
        /**
         * INCR と EXPIRE の間で落ちると期限の無いキーが残り、締め出しが解けなくなるので 1 回で行う
         */
        const val INCREMENT_WITH_TTL_SCRIPT = """
            local count = redis.call('INCR', KEYS[1])
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            return count
        """
    }
}
