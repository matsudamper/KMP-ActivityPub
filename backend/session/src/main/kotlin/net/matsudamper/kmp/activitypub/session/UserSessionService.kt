package net.matsudamper.kmp.activitypub.session

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import net.matsudamper.kmp.activitypub.shared.AccountId

class UserSessionService(
    private val sessionStore: UserSessionStore,
    private val ttl: Duration,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    val ttlSeconds: Long get() = ttl.inWholeSeconds

    fun create(accountId: AccountId): String {
        val token = BASE64_ENCODER.encodeToString(ByteArray(TOKEN_SIZE_BYTES).also(random::nextBytes))
        val now = clock.instant()
        sessionStore.create(
            tokenHash = hash(token),
            accountId = accountId,
            createdAt = now,
            expiresAt = now.plus(ttl.toJavaDuration()),
        )
        return token
    }

    fun accountIdOf(token: String): AccountId? = sessionStore.findAccountId(tokenHash = hash(token), now = clock.instant())

    /**
     * 推測できない長さの乱数なので salt や反復は要らない
     */
    private fun hash(token: String): String =
        BASE64_ENCODER.encodeToString(MessageDigest.getInstance("SHA-256").digest(token.encodeToByteArray()))

    private companion object {
        const val TOKEN_SIZE_BYTES = 32

        val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
