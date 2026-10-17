package net.matsudamper.kmp.activitypub.session

import java.time.Instant
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import net.matsudamper.kmp.activitypub.shared.AccountId

class ValkeyUserSessionStore(
    uri: String,
) : UserSessionStore {
    private val client: RedisClient = RedisClient.create(uri)
    private val connection: StatefulRedisConnection<String, String> = client.connect()

    override fun create(
        tokenHash: String,
        accountId: AccountId,
        createdAt: Instant,
        expiresAt: Instant,
    ) {
        connection.sync().set(
            keyOf(tokenHash),
            accountId.value.toString(),
            SetArgs.Builder.pxAt(expiresAt.toEpochMilli()),
        )
    }

    override fun findAccountId(
        tokenHash: String,
        now: Instant,
    ): AccountId? = connection.sync().get(keyOf(tokenHash))?.let { AccountId(it.toLong()) }

    override fun close() {
        connection.close()
        client.shutdown()
    }

    private fun keyOf(tokenHash: String): String = "user-session:$tokenHash"
}
