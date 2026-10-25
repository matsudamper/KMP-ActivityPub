package net.matsudamper.kmp.activitypub.session

import java.time.Instant
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import net.matsudamper.kmp.activitypub.shared.AccountId

class ValkeyPasskeyChallengeStore(
    uri: String,
) : PasskeyChallengeStore {
    private val client: RedisClient = RedisClient.create(uri)
    private val connection: StatefulRedisConnection<String, String> = client.connect()

    override fun add(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
        expiresAt: Instant,
    ) {
        connection.sync().set(
            keyOf(challenge),
            valueOf(purpose, accountId),
            SetArgs.Builder.pxAt(expiresAt.toEpochMilli()),
        )
    }

    override fun consume(
        challenge: String,
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
    ): Boolean = connection.sync().getdel(keyOf(challenge)) == valueOf(purpose, accountId)

    override fun close() {
        connection.close()
        client.shutdown()
    }

    private fun keyOf(challenge: String): String = "passkey-challenge:$challenge"

    private fun valueOf(
        purpose: PasskeyChallengePurpose,
        accountId: AccountId?,
    ): String = "${purpose.name}:${accountId?.value ?: ""}"
}
