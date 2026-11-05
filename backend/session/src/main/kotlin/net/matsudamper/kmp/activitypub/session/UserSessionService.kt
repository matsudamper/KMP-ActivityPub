package net.matsudamper.kmp.activitypub.session

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
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

    /**
     * 使われている間は期限を ttl 先まで延ばす。
     * 延ばすたびに Cookie を出し直すとアプリが保存とバックアップを毎回やり直すので、延ばすのは 1 日に 1 回までにする
     */
    fun authenticate(
        token: String,
        extendable: Boolean,
    ): AuthenticatedUserSession? {
        val tokenHash = hash(token)
        val now = clock.instant()
        val stored = sessionStore.find(tokenHash = tokenHash, now = now) ?: return null
        val extendedExpiresAt = now.plus(ttl.toJavaDuration())
        val extended = extendable && stored.expiresAt.plus(EXTEND_INTERVAL.toJavaDuration()) <= extendedExpiresAt
        if (extended && !sessionStore.extend(tokenHash = tokenHash, expiresAt = extendedExpiresAt)) {
            return null
        }
        return AuthenticatedUserSession(accountId = stored.accountId, extended = extended)
    }

    fun delete(token: String) {
        sessionStore.delete(tokenHash = hash(token))
    }

    /**
     * 推測できない長さの乱数なので salt や反復は要らない
     */
    private fun hash(token: String): String =
        BASE64_ENCODER.encodeToString(MessageDigest.getInstance("SHA-256").digest(token.encodeToByteArray()))

    private companion object {
        const val TOKEN_SIZE_BYTES = 32

        val EXTEND_INTERVAL: Duration = 1.days

        val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}

data class AuthenticatedUserSession(
    val accountId: AccountId,
    val extended: Boolean,
)
