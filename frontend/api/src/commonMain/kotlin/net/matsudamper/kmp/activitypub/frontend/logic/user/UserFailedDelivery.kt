package net.matsudamper.kmp.activitypub.frontend.logic.user

import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.FailedDeliveryFields

/**
 * 送るのを諦めた配信
 *
 * @param kind サーバーの配信キューの種別の綴り。accept_follow / create_note など
 * @param username 署名したこのサーバーのアカウント
 * @param attempts 送ろうとした回数
 * @param failedAt 諦めた時刻を残すようにする前に諦めたものは null
 * @param lastError 最後に送れなかった理由
 */
data class UserFailedDelivery(
    val id: String,
    val kind: String,
    val username: String,
    val inbox: String,
    val attempts: Int,
    val enqueuedAt: String,
    val failedAt: String?,
    val lastError: String?,
)

internal fun FailedDeliveryFields.toUserFailedDelivery(): UserFailedDelivery = UserFailedDelivery(
    id = id,
    kind = kind,
    username = username,
    inbox = inbox,
    attempts = attempts,
    enqueuedAt = enqueuedAt,
    failedAt = failedAt,
    lastError = lastError,
)
