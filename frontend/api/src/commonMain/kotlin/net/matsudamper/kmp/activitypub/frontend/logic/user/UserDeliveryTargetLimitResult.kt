package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserDeliveryTargetLimitResult {
    /**
     * @param maxPerHost 同じホストからのフォローで配る先の上限
     * @param hosts 配る先の数の多い順
     */
    data class Success(
        val maxPerHost: Int,
        val hosts: List<UserDeliveryTargetHost>,
    ) : UserDeliveryTargetLimitResult

    data object NotLoggedIn : UserDeliveryTargetLimitResult

    data class Failure(
        val message: String,
    ) : UserDeliveryTargetLimitResult
}

data class UserDeliveryTargetHost(
    val host: String,
    val deliveryTargetCount: Int,
)
