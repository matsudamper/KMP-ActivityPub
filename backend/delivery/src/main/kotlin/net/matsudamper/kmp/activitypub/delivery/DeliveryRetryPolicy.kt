package net.matsudamper.kmp.activitypub.delivery

import java.time.Instant
import kotlin.time.Duration
import kotlin.time.toJavaDuration

/**
 * 送れなかった配信を次にいつ送るか。
 * 間を 2 倍ずつ空けて、投函から [giveUpAfter] を過ぎるなら諦める。
 * 相手が数日止まっていても復帰後に届き、消えたサーバーには送り続けない
 *
 * @param initialDelay 最初の失敗の後に待つ時間
 * @param maxDelay 間隔の上限
 */
class DeliveryRetryPolicy(
    private val initialDelay: Duration,
    private val maxDelay: Duration,
    private val giveUpAfter: Duration,
) {
    /**
     * @param attempts 失敗した取り出しを含めて取り出された回数。1 回目の失敗なら 1
     * @return 諦めるなら null
     */
    fun nextAttemptAt(
        attempts: Int,
        enqueuedAt: Instant,
        now: Instant,
    ): Instant? {
        val retryDelay = (initialDelay * (1 shl (attempts - 1).coerceIn(0, MAX_BACKOFF_SHIFT))).coerceAtMost(maxDelay)
        val next = now.plus(retryDelay.toJavaDuration())
        return next.takeIf { it <= enqueuedAt.plus(giveUpAfter.toJavaDuration()) }
    }

    private companion object {
        // Int の 2 のべき乗が溢れない範囲。これだけ伸ばせば maxDelay に届く
        const val MAX_BACKOFF_SHIFT = 20
    }
}
