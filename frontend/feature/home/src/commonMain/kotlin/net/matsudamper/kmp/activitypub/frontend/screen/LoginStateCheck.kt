package net.matsudamper.kmp.activitypub.frontend.screen

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLoginStateResult

private val LOGIN_STATE_FIRST_RETRY_DELAY = 5.seconds
private val LOGIN_STATE_MAX_RETRY_DELAY = 60.seconds

/**
 * ログインしているかを、確かめられるまで間を延ばしながら問い合わせる。
 * ログインしていない人でも開ける画面は、確かめられないうちは操作を出さず、画面を開き直さなくても出せるようにする
 */
internal suspend fun UserApi.awaitLoggedIn(): Boolean {
    var retryDelay = LOGIN_STATE_FIRST_RETRY_DELAY
    while (true) {
        when (loginState()) {
            UserLoginStateResult.LoggedIn -> return true

            UserLoginStateResult.NotLoggedIn -> return false

            is UserLoginStateResult.Failure -> {
                delay(retryDelay)
                retryDelay = (retryDelay * 2).coerceAtMost(LOGIN_STATE_MAX_RETRY_DELAY)
            }
        }
    }
}
