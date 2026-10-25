package net.matsudamper.kmp.activitypub.frontend.logic.admin

sealed interface AdminAccountsResult {
    data class Success(
        val accounts: List<AdminAccount>,
        /**
         * 続きが無ければ null
         */
        val nextCursor: String?,
    ) : AdminAccountsResult

    data class Failure(
        val message: String,
    ) : AdminAccountsResult
}
