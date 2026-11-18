package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserFailedDeliveriesResult {
    /**
     * @param deliveries 新しく積んだ順
     * @param nextCursor 続きが無ければ null
     */
    data class Success(
        val deliveries: List<UserFailedDelivery>,
        val nextCursor: String?,
    ) : UserFailedDeliveriesResult

    data object NotLoggedIn : UserFailedDeliveriesResult
}
