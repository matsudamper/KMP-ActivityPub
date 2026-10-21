package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.runtime.Immutable

data class HomeScreenUiState(
    val selectedTab: Tab,
    val timelineItems: List<TimelineItem>,
    val timelineRefreshing: Boolean,
    val search: Search,
    val listener: Listener,
) {
    enum class Tab {
        Home,
        Search,
    }

    data class TimelineItem(
        val displayName: String,
        val acct: String,
        val body: String,
        val postedAt: String,
    )

    data class Search(
        val query: String,
        val accounts: List<SearchAccount>,
    )

    data class SearchAccount(
        val displayName: String,
        val acct: String,
    )

    @Immutable
    interface Listener {
        fun onClickPost()

        fun onClickSettings()

        fun onClickTab(tab: Tab)

        fun onRefreshTimeline()

        fun onSearchQueryChanged(query: String)

        fun onSearch()
    }
}
