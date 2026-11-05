package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.runtime.Immutable
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

data class HomeScreenUiState(
    val selectedTab: Tab,
    val timelineItems: List<PostItemUiState>,
    val timelineRefreshing: Boolean,
    val timelineFooter: TimelineFooter,
    val timelineStreamStatus: TimelineStreamStatus,
    val sensitiveImagesBlurred: Boolean,
    val search: Search,
    val profile: Profile,
    val userTabIconUrl: String?,
    /**
     * 出していなければ null
     */
    val deleteNoteDialog: DeleteNoteDialog?,
    val listener: Listener,
) {
    data class DeleteNoteDialog(
        val deleteButtonEnabled: Boolean,
    )

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

        fun onClickFollowing()

        fun onClickFollowers()

        fun onClickConfirmDeleteNote()

        fun onDismissDeleteNoteDialog()

        fun onScreenShown()
    }
}
