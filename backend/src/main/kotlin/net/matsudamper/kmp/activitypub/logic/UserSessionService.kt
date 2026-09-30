package net.matsudamper.kmp.activitypub.logic

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.util.Base64
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import net.matsudamper.kmp.activitypub.repository.UserSessionRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * ユーザーのログインセッション。
 *
 * 管理画面と違って DB に置く。Android アプリは開くたびにログインし直させられないので、
 * サーバーの再起動でセッションが消えると困る。
 */
class UserSessionService(
    private val sessions: UserSessionRepository,
    private val ttl: Duration,
    private val clock: Clock,
) {
    private val random = SecureRandom()

    val ttlSeconds: Long get() = ttl.inWholeSeconds

    /**
     * @return Cookie に入れるトークン
     */
    fun create(accountId: AccountId): String {
        val token = BASE64_ENCODER.encodeToString(ByteArray(TOKEN_SIZE_BYTES).also(random::nextBytes))
        val now = clock.instant()
        sessions.create(
            tokenHash = hash(token),
            accountId = accountId,
            createdAt = now,
            expiresAt = now.plus(ttl.toJavaDuration()),
        )
        return token
    }

    fun accountIdOf(token: String): AccountId? = sessions.findAccountId(tokenHash = hash(token), now = clock.instant())

    fun delete(token: String) {
        sessions.delete(hash(token))
    }

    /**
     * トークンは推測できない長さの乱数なので、総当たりへの備え（salt や反復）は要らない
     */
    private fun hash(token: String): String =
        BASE64_ENCODER.encodeToString(MessageDigest.getInstance("SHA-256").digest(token.encodeToByteArray()))

    private companion object {
        const val TOKEN_SIZE_BYTES = 32

        val BASE64_ENCODER: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
    }
}
