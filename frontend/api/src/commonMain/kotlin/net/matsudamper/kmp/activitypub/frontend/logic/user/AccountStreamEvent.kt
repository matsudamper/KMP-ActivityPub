package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface AccountStreamEvent {
    data object Subscribing : AccountStreamEvent

    data class PostArrived(val post: UserHomeTimelineResult.Post) : AccountStreamEvent

    /**
     * 投稿者が投稿を消したか、ブーストした相手がブーストを取り消した
     */
    data class PostsRemoved(val removals: PostRemovals) : AccountStreamEvent

    data class NotificationArrived(val notification: UserNotificationsResult.Notification) : AccountStreamEvent

    data object WaitingToReconnect : AccountStreamEvent

    /**
     * つなぎ直しが続けて失敗したので、もう自動ではつなぎ直さない
     */
    data object GaveUp : AccountStreamEvent
}
