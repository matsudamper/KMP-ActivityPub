package net.matsudamper.kmp.activitypub.session

import kotlin.time.Duration

/**
 * 照合の前に isLocked を見る。照合そのものが重いので、締め出した後は照合を回さない。
 * 締め出し中の試行も recordFailure に渡すと、締め出しが最後の試行から lockDuration に延びる
 */
class LoginThrottle(
    private val failureStore: LoginFailureStore,
    private val maxFailures: Int,
    private val lockDuration: Duration,
) {
    fun isLocked(clientIp: String): Boolean = failureStore.count(clientIp) >= maxFailures

    fun recordFailure(clientIp: String) {
        failureStore.increment(clientIp = clientIp, ttl = lockDuration)
    }

    fun recordSuccess(clientIp: String) {
        failureStore.clear(clientIp)
    }
}
