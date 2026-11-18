package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface UserNotificationsResult {
    data class Success(val notifications: List<Notification>, val nextCursor: String?) : UserNotificationsResult
    data object NotLoggedIn : UserNotificationsResult

    /**
     * 通知カラムの 1 項目。お気に入りとブーストは投稿ごと・種類ごとにまとまり、フォローとメンションは 1 件ずつ
     *
     * @param actors 新しく加わった順の相手。数人まで
     * @param actorCount 相手の人数
     * @param post 対象の投稿。フォローでは null。メンションでは宛てた投稿、ほかは自分の投稿
     * @param updatedAt 最後に新しい相手が加わった時刻。この新しい順に並ぶ
     */
    data class Notification(
        val id: String,
        val kind: Kind,
        val actors: List<NotificationActor>,
        val actorCount: Int,
        val post: UserHomeTimelineResult.Post?,
        val updatedAt: String,
    )

    /**
     * @param reaction お気に入りで付けた絵文字。絵文字の無いいいねと、ほかの種類では null
     */
    data class NotificationActor(
        val actor: UserHomeTimelineResult.Actor,
        val reaction: Reaction?,
    )

    /**
     * @param imageUrl カスタム絵文字の画像。Unicode の絵文字か、画像が無ければ null
     */
    data class Reaction(
        val emoji: String,
        val imageUrl: String?,
    )

    enum class Kind {
        Follow,
        Mention,
        Favourite,
        Boost,
    }
}
