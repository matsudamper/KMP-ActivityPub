package net.matsudamper.kmp.activitypub.session

import kotlin.time.Duration

interface LoginFailureStore : AutoCloseable {
    /**
     * 送信元の失敗の合計が maxFailures 未満なら username に 1 足して true を返す。
     * 足したかどうかに関わらず、送信元の記録全体の期限を ttl に延ばす
     */
    fun reserve(
        clientIp: String,
        username: String,
        maxFailures: Int,
        ttl: Duration,
    ): Boolean

    fun clear(
        clientIp: String,
        username: String,
    )
}
