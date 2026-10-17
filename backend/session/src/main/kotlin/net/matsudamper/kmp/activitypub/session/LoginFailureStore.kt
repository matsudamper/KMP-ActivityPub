package net.matsudamper.kmp.activitypub.session

import kotlin.time.Duration

interface LoginFailureStore : AutoCloseable {
    /**
     * 送信元の失敗を、どのユーザー名に対するものかを問わず合計する
     */
    fun totalCount(clientIp: String): Long

    /**
     * 送信元の記録全体の期限を、最後の失敗から数え直す
     */
    fun increment(
        clientIp: String,
        username: String,
        ttl: Duration,
    )

    fun extend(
        clientIp: String,
        ttl: Duration,
    )

    fun clear(
        clientIp: String,
        username: String,
    )
}
