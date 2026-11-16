package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface AccountStreamEvent {
    data object Subscribing : AccountStreamEvent

    data class PostArrived(val post: UserHomeTimelineResult.Post) : AccountStreamEvent

    /**
     * 投稿者が投稿を消したか、ブーストした相手がブーストを取り消した
     */
    data class PostsRemoved(val removals: PostRemovals) : AccountStreamEvent

    /**
     * 投稿者が編集したか、付いたリアクションが増減した。[post] は変わった後の行
     */
    data class PostUpdated(val post: UserHomeTimelineResult.Post) : AccountStreamEvent

    data class NotificationArrived(val notification: UserNotificationsResult.Notification) : AccountStreamEvent

    data object WaitingToReconnect : AccountStreamEvent

    /**
     * つなぎ直しが続けて失敗したので、もう自動ではつなぎ直さない
     */
    data object GaveUp : AccountStreamEvent
}
