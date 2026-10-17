package net.matsudamper.kmp.activitypub.session

import kotlin.time.Duration

/**
 * 照合の前に tryReserve で失敗を 1 回分先に数える。
 * 照合の後に数えると、同時に送られたリクエストが数える前の判定をすり抜けて上限を超えて照合できる
 */
class LoginThrottle(
    private val failureStore: LoginFailureStore,
    private val maxFailures: Int,
    private val lockDuration: Duration,
) {
    /**
     * false なら締め出し中なので照合しない。締め出しは最後の試行から lockDuration に延びる
     */
    fun tryReserve(
        clientIp: String,
        username: String,
    ): Boolean =
        failureStore.reserve(
            clientIp = clientIp,
            username = username,
            maxFailures = maxFailures,
            ttl = lockDuration,
        )

    /**
     * 自分のアカウントで成功して他のアカウントへの失敗まで消せないよう、成功したユーザー名の分だけ消す
     */
    fun recordSuccess(
        clientIp: String,
        username: String,
    ) {
        failureStore.clear(clientIp = clientIp, username = username)
    }
}
