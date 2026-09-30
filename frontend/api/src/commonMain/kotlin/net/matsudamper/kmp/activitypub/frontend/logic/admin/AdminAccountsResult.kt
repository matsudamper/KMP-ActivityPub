package net.matsudamper.kmp.activitypub.frontend.logic.admin

sealed interface AdminAccountsResult {
    /**
     * @param nextCursor 続きがあれば、それを取るために渡す印
     */
    data class Success(
        val accounts: List<AdminAccount>,
        val hasMore: Boolean,
        val nextCursor: String?,
    ) : AdminAccountsResult

    data class Failure(
        val message: String,
    ) : AdminAccountsResult
}
