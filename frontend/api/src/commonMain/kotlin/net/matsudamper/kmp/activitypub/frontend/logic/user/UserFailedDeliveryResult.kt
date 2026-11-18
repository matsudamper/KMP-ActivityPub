package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserFailedDeliveryResult {
    data class Found(
        val delivery: UserFailedDelivery,
    ) : UserFailedDeliveryResult

    data object NotFound : UserFailedDeliveryResult

    data object NotLoggedIn : UserFailedDeliveryResult

    data class Failure(
        val message: String,
    ) : UserFailedDeliveryResult
}
