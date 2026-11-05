package net.matsudamper.kmp.activitypub.session

import java.time.Instant
import io.lettuce.core.ClientOptions
import io.lettuce.core.MaintNotificationsConfig
import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.resource.ClientResources
import io.opentelemetry.instrumentation.lettuce.v5_1.LettuceTelemetry
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer

class ValkeyUserSessionStore(
    uri: String,
) : UserSessionStore {
    private val clientResources: ClientResources =
        ClientResources
            .builder()
            .tracing(LettuceTelemetry.create(OpenTelemetryInitializer.get()).createTracing())
            .build()
    private val client: RedisClient = RedisClient.create(clientResources, uri).apply {
        setOptions(ClientOptions.builder().maintNotificationsConfig(MaintNotificationsConfig.disabled()).build())
    }
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

    override fun find(
        tokenHash: String,
        now: Instant,
    ): StoredUserSession? {
        val key = keyOf(tokenHash)
        val accountId = connection.sync().get(key) ?: return null
        val expiresAtMillis = connection.sync().pexpiretime(key)
        // GET の直後に期限が切れると負の値が返る
        if (expiresAtMillis < 0) return null
        return StoredUserSession(
            accountId = AccountId(accountId.toLong()),
            expiresAt = Instant.ofEpochMilli(expiresAtMillis),
        )
    }

    override fun extend(
        tokenHash: String,
        expiresAt: Instant,
    ) {
        connection.sync().pexpireat(keyOf(tokenHash), expiresAt.toEpochMilli())
    }

    override fun delete(tokenHash: String) {
        connection.sync().del(keyOf(tokenHash))
    }

    override fun close() {
        connection.close()
        client.shutdown()
        clientResources.shutdown()
    }

    private fun keyOf(tokenHash: String): String = "user-session:$tokenHash"
}
