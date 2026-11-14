package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserBlockedAccountsResult {
    /**
     * @param accounts 新しくブロックした順
     */
    data class Success(
        val accounts: List<UserBlockedAccount>,
    ) : UserBlockedAccountsResult

    data class Failure(
        val message: String,
    ) : UserBlockedAccountsResult
}
