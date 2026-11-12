package net.matsudamper.kmp.activitypub.shared

/**
 * 通知の識別子。新しい通知ほど大きい
 */
data class NotificationId(
    val value: Long,
)
