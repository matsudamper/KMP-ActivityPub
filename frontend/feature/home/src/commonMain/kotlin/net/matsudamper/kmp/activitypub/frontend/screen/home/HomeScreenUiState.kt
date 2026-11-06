package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaUiState

data class HomeScreenUiState(
    val selectedTab: Tab,
    val timelineItems: List<PostItemUiState>,
    val timelineRefreshing: Boolean,
    val timelineFooter: TimelineFooter,
    val timelineStreamStatus: TimelineStreamStatus,
    val sensitiveImagesBlurred: Boolean,
    val search: Search,
    val profile: Profile,
    val userContentTab: ProfileContentTab,
    val userPosts: UserPosts,
    val userTabIconUrl: String?,
    val listener: Listener,
) {
    sealed interface TimelineFooter {
        data object None : TimelineFooter
        data object Loading : TimelineFooter
        data class Error(val message: String) : TimelineFooter
    }

    enum class TimelineStreamStatus {
        Receiving,
        WaitingToReconnect,
        Disconnected,
    }

    enum class Tab {
        Home,
        Search,
        User,
    }

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

    sealed interface Profile {
        data object Loading : Profile

        data class Error(
            val message: String,
        ) : Profile

        data class Loaded(
            val iconUrl: String?,
            val displayName: String,
            val acct: String,
            val followingCount: String,
            val followersCount: String,
        ) : Profile
    }

    sealed interface UserPosts {
        data object Loading : UserPosts

        data class Loaded(
            val items: List<PostItemUiState>,
            val emptyMessage: String?,
            val media: List<ProfileMediaUiState>,
            val mediaEmptyMessage: String?,
        ) : UserPosts

        data object LoadFailed : UserPosts
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

        fun onScrolledToTimelineEnd()

        fun onClickRetryTimelineLoadMore()

        fun onClickTimelineStreamStatus()

        fun onClickSensitiveImagesVisibility()

        fun onSearchQueryChanged(query: String)

        fun onSearch()

        fun onClickLoadMoreSearchResult()

        fun onClickEditProfile()

        fun onClickRetryProfile()

        fun onClickRetryUserPosts()

        fun onClickUserContentTab(tab: ProfileContentTab)

        fun onClickFollowing()

        fun onClickFollowers()

        fun onScreenShown()
    }
}
