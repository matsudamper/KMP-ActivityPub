package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface HomeTimelineStreamEvent {
    data object Subscribing : HomeTimelineStreamEvent

    data class Arrived(val post: UserHomeTimelineResult.Post) : HomeTimelineStreamEvent

    data object WaitingToReconnect : HomeTimelineStreamEvent

    /**
     * つなぎ直しが続けて失敗したので、もう自動ではつなぎ直さない
     */
    data object GaveUp : HomeTimelineStreamEvent
}
