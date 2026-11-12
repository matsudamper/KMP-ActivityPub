package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserNotificationsResult {
    data class Success(val notifications: List<Notification>, val nextCursor: String?) : UserNotificationsResult
    data object NotLoggedIn : UserNotificationsResult

    /**
     * @param post お気に入りかブーストをされた、自分の投稿
     */
    data class Notification(
        val id: String,
        val kind: Kind,
        val actor: UserHomeTimelineResult.Actor,
        val post: UserHomeTimelineResult.Post,
        val receivedAt: String,
    )

    enum class Kind {
        Favourite,
        Boost,
    }
}
