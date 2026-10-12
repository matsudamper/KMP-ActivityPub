package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.runtime.Immutable

data class HomeScreenUiState(
    val timelineItems: List<TimelineItem>,
    val listener: Listener,
) {
    data class TimelineItem(
        val displayName: String,
        val acct: String,
        val body: String,
        val postedAt: String,
    )

    @Immutable
    interface Listener {
        fun onClickPost()
    }
}
