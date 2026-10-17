package net.matsudamper.kmp.activitypub.session

import kotlin.time.Duration
import io.lettuce.core.RedisClient
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
        val commands = connection.sync()
        commands.incr(keyOf(clientIp))
        commands.expire(keyOf(clientIp), ttl.inWholeSeconds)
    }

    override fun clear(clientIp: String) {
        connection.sync().del(keyOf(clientIp))
    }

    override fun close() {
        connection.close()
        client.shutdown()
    }

    private fun keyOf(clientIp: String): String = "login-failure:$clientIp"
}
