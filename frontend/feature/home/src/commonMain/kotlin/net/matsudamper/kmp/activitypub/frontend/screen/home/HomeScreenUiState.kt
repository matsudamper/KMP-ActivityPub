package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.runtime.Immutable

data class HomeScreenUiState(
    val selectedTab: Tab,
    val timelineItems: List<TimelineItem>,
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
        val result: SearchResult,
    )

    sealed interface SearchResult {
        data object NotSearched : SearchResult

        data object Loading : SearchResult

        data class Loaded(
            val accounts: List<SearchAccount>,
            val canLoadMore: Boolean,
        ) : SearchResult

        data class Error(
            val message: String,
        ) : SearchResult
    }

    data class SearchAccount(
        val displayName: String,
        val acct: String,
        val listener: SearchAccountListener,
    )

    @Immutable
    interface SearchAccountListener {
        fun onClick()
    }

    @Immutable
    interface Listener {
        fun onClickPost()

        fun onClickSettings()

        fun onClickTab(tab: Tab)

        fun onSearchQueryChanged(query: String)

        fun onSearch()

        fun onClickLoadMoreSearchResult()
    }
}
