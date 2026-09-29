package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserSessionResult {
    /**
     * @param acct Mastodon の検索窓に貼る形
     */
    data class LoggedIn(
        val username: String,
        val acct: String,
    ) : UserSessionResult

    data object LoggedOut : UserSessionResult

    data class Failure(
        val message: String,
    ) : UserSessionResult
}
