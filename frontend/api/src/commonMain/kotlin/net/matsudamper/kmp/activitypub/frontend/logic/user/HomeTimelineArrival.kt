package net.matsudamper.kmp.activitypub.frontend.logic.user

sealed interface HomeTimelineArrival {
    data class Arrived(val post: UserHomeTimelineResult.Post) : HomeTimelineArrival

    /**
     * つなぎ直しが続けて失敗したので、もう自動ではつなぎ直さない
     */
    data object GaveUp : HomeTimelineArrival
}
