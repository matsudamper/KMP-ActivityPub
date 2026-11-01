package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.runtime.Immutable

data class HomeScreenUiState(
    val selectedTab: Tab,
    val timelineItems: List<TimelineItem>,
    val timelineRefreshing: Boolean,
    val search: Search,
    val profile: Profile,
    val following: Following,
    val userTabIconUrl: String?,
    val listener: Listener,
) {
    enum class Tab {
        Home,
        Search,
        User,
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
            val loadMoreButtonVisible: Boolean,
        ) : SearchResult
    }

    data class SearchAccount(
        val followButtonText: String?,
        val followButtonEnabled: Boolean,
        val onClickFollow: () -> Unit,
        val iconUrl: String?,
        val displayName: String,
        val acct: String,
        /**
         * 押しても何も起きないものは null
         */
        val listener: SearchAccountListener?,
    )

    sealed interface Following {
        data object Loading : Following

        /**
         * 読めなかった理由はメッセージで出す
         */
        data object NotLoaded : Following

        data class Loaded(
            val accounts: List<SearchAccount>,
            val loadMoreButtonVisible: Boolean,
        ) : Following
    }

    sealed interface Profile {
        data object Loading : Profile

        data class Error(
            val message: String,
        ) : Profile

        data class Loaded(
            val iconUrl: String?,
            val displayName: String,
            val acct: String,
        ) : Profile
    }

    @Immutable
    interface SearchAccountListener {
        fun onClick()
    }

    @Immutable
    interface Listener {
        fun onClickPost()

        fun onClickSettings()

        fun onClickTab(tab: Tab)

        fun onTabChanged(tab: Tab)

        fun onRefreshTimeline()

        fun onSearchQueryChanged(query: String)

        fun onSearch()

        fun onClickLoadMoreSearchResult()

        fun onClickEditProfile()

        fun onClickRetryProfile()

        fun onClickLoadMoreFollowing()

        fun onClickRetryFollowing()

        fun onScreenShown()
    }
}
