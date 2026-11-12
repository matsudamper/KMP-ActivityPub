package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.NotificationRepository
import net.matsudamper.kmp.activitypub.repository.ReceivedNotification
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.NotificationId

/**
 * 他のサーバーから届いた、自分の投稿へのお気に入りとブーストの通知を読む
 */
class NotificationService(
    private val notifications: NotificationRepository,
    private val timeline: TimelineRepository,
) {
    /**
     * @param cursor 前回の [NotificationPage.nextCursor]。先頭から読むなら null
     */
    fun page(accountId: AccountId, cursor: String?, limit: Int): NotificationPage {
        val after = cursor?.let { it.toLongOrNull() }?.let { NotificationId(it) }
        require(cursor == null || after != null) { "cursor が読めない" }
        val pageSize = limit.coerceIn(1, 100)
        val received = notifications.page(accountId, after, pageSize + 1)
        val selected = received.take(pageSize)
        val posts = timeline.hydrate(accountId, selected.map { it.noteMember }.distinct()).associateBy { it.position.member }
        return NotificationPage(
            notifications = selected.mapNotNull { notification -> posts[notification.noteMember]?.let { NotificationWithPost(notification, it) } },
            nextCursor = if (received.size > pageSize) selected.lastOrNull()?.id?.value?.toString() else null,
        )
    }
}

/**
 * @param post お気に入りかブーストをされた投稿
 */
data class NotificationWithPost(val notification: ReceivedNotification, val post: TimelinePost)

data class NotificationPage(val notifications: List<NotificationWithPost>, val nextCursor: String?)
