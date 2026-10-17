package net.matsudamper.kmp.activitypub.session

import kotlin.time.Duration

/**
 * 照合の前に isLocked を見る。照合そのものが重いので、締め出した後は照合を回さない。
 * 締め出し中の試行は extendLock に渡し、締め出しを最後の試行から lockDuration に延ばす
 */
class LoginThrottle(
    private val failureStore: LoginFailureStore,
    private val maxFailures: Int,
    private val lockDuration: Duration,
) {
    fun isLocked(clientIp: String): Boolean = failureStore.totalCount(clientIp) >= maxFailures

    fun recordFailure(
        clientIp: String,
        username: String,
    ) {
        failureStore.increment(clientIp = clientIp, username = username, ttl = lockDuration)
    }

    /**
     * 自分のアカウントで成功して他のアカウントへの失敗まで消せないよう、成功したユーザー名の分だけ消す
     */
    fun recordSuccess(
        clientIp: String,
        username: String,
    ) {
        failureStore.clear(clientIp = clientIp, username = username)
    }

    fun extendLock(clientIp: String) {
        failureStore.extend(clientIp = clientIp, ttl = lockDuration)
    }
}
