package net.matsudamper.kmp.activitypub.timeline

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import net.matsudamper.kmp.activitypub.repository.ReceivedNotification
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * サーバーは 1 台の前提なので、アカウントに起きた変化はプロセス内だけで購読者へ流す。
 * 購読者が詰まっても配布を止めないよう、溢れたら古いものから捨てる
 */
class AccountEvents(
    private val repository: TimelineRepository,
) {
    private val signals = MutableSharedFlow<Signal>(
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun publishArrival(recipients: List<AccountId>, position: TimelinePosition) {
        if (recipients.isEmpty()) return
        signals.tryEmit(Signal.Arrival(recipients.toSet(), position))
    }

    /**
     * @param members 削除済みになったタイムラインの member
     */
    fun publishDeletion(recipients: List<AccountId>, members: List<String>) {
        if (recipients.isEmpty() || members.isEmpty()) return
        signals.tryEmit(Signal.Deletion(recipients.toSet(), members))
    }

    fun publishNotification(notification: ReceivedNotification) {
        signals.tryEmit(Signal.Notification(notification))
    }

    /**
     * 投稿を DB から読むので、blocking してよい dispatcher で集める
     */
    fun events(accountId: AccountId): Flow<AccountEvent> =
        signals
            .filter { accountId in it.recipients }
            .mapNotNull { signal ->
                when (signal) {
                    is Signal.Arrival -> repository.hydrate(accountId, listOf(signal.position.member)).singleOrNull()?.let { AccountEvent.PostArrived(it) }

                    is Signal.Deletion -> AccountEvent.PostsDeleted(signal.members)

                    is Signal.Notification -> repository.hydrate(accountId, listOf(signal.notification.noteMember)).singleOrNull()
                        ?.let { AccountEvent.NotificationArrived(signal.notification, it) }
                }
            }

    private sealed interface Signal {
        val recipients: Set<AccountId>

        class Arrival(override val recipients: Set<AccountId>, val position: TimelinePosition) : Signal

        class Deletion(override val recipients: Set<AccountId>, val members: List<String>) : Signal

        class Notification(val notification: ReceivedNotification) : Signal {
            override val recipients: Set<AccountId> = setOf(notification.accountId)
        }
    }
}

sealed interface AccountEvent {
    data class PostArrived(val post: TimelinePost) : AccountEvent

    /**
     * @param members 削除済みになったタイムラインの member
     */
    data class PostsDeleted(val members: List<String>) : AccountEvent

    /**
     * @param post お気に入りかブーストをされた投稿
     */
    data class NotificationArrived(val notification: ReceivedNotification, val post: TimelinePost) : AccountEvent
}
