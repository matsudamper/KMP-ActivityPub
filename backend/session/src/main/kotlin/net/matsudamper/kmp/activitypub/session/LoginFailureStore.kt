package net.matsudamper.kmp.activitypub.session

import kotlin.time.Duration

interface LoginFailureStore : AutoCloseable {
    fun count(clientIp: String): Long

    /**
     * 期限は最後の失敗から数え直す
     */
    fun increment(
        clientIp: String,
        ttl: Duration,
    )

    fun clear(clientIp: String)
}
