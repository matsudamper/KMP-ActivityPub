package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.NotificationKind
import net.matsudamper.kmp.activitypub.repository.NotificationRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.timeline.AccountEvents

/**
 * お気に入りやブーストと一緒に記録された通知を、つながっている投稿者へ流す
 */
internal class ReceivedNotifications(
    private val repository: NotificationRepository,
    private val events: AccountEvents,
) {
    fun publish(kind: NotificationKind, notePublicId: PublicNoteId, actorUri: String) {
        val notification = repository.find(kind = kind, notePublicId = notePublicId, actorUri = actorUri) ?: return
        events.publishNotification(notification)
    }
}
