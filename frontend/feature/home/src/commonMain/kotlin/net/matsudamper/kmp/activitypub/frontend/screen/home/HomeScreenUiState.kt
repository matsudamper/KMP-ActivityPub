package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.runtime.Immutable

data class HomeScreenUiState(
    val selectedTab: Tab,
    val timelineItems: List<TimelineItem>,
    val timelineRefreshing: Boolean,
    val timelineFooter: TimelineFooter,
    val search: Search,
    val profile: Profile,
    val userTabIconUrl: String?,
    val listener: Listener,
) {
    sealed interface TimelineFooter {
        data object None : TimelineFooter
        data object Loading : TimelineFooter
        data class Error(val message: String) : TimelineFooter
    }

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
        /**
         * 本文のリンクごとのカード。OGP が取れるまでは URL だけで埋めておく
         */
        val linkPreviews: List<LinkPreview>,
        val listener: TimelineItemListener,
    )

    /**
     * @param imageUrl null でも画像の枠は出す
     */
    data class LinkPreview(
        val title: String,
        val siteName: String,
        val imageUrl: String?,
        val listener: LinkPreviewListener,
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
    interface TimelineItemListener {
        /**
         * 投稿が画面に入ったとき。OGP はここで初めて取りに行く
         */
        fun onVisible()

        fun onClickAuthor()
    }

    @Immutable
    interface LinkPreviewListener {
        fun onClick()
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

        fun onSearchQueryChanged(query: String)

        fun onSearch()

        fun onClickLoadMoreSearchResult()

        fun onClickEditProfile()

        fun onClickRetryProfile()

        fun onClickFollowing()

        fun onClickFollowers()

        fun onScreenShown()
    }
}
