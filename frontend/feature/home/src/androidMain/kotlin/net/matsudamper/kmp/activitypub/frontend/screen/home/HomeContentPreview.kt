package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler

@Preview
@Composable
private fun HomeContentPreview() {
    HomePreviewTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Home,
                timelineItems = listOf(
                    HomeScreenUiState.TimelineItem(
                        displayName = "Kotlin",
                        acct = "@kotlin@example.com",
                        body = "Kotlin 2.4 がリリースされました",
                        postedAt = "1分前",
                    ),
                    HomeScreenUiState.TimelineItem(
                        displayName = "Compose",
                        acct = "@compose@example.com",
                        body = "Compose Multiplatform で Web と Android の画面を共有している",
                        postedAt = "5分前",
                    ),
                ),
                timelineRefreshing = false,
                search = HomeScreenUiState.Search(
                    query = "",
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loading,
                following = HomeScreenUiState.Following.Loading,
                userTabIconUrl = null,
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun HomeSearchContentPreview() {
    HomePreviewTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Search,
                timelineItems = listOf(),
                timelineRefreshing = false,
                search = HomeScreenUiState.Search(
                    query = "kotlin",
                    result = HomeScreenUiState.SearchResult.Loaded(
                        accounts = listOf(
                            HomeScreenUiState.SearchAccount(
                                iconUrl = "https://example.com/icon.png",
                                displayName = "kotlin",
                                acct = "@kotlin",
                                followButtonText = null,
                                followButtonEnabled = false,
                                onClickFollow = {},
                                listener = AndroidPreviewSearchAccountListener,
                            ),
                            HomeScreenUiState.SearchAccount(
                                iconUrl = null,
                                displayName = "Kotlin Weekly",
                                acct = "@kotlinweekly@remote.example",
                                followButtonText = "フォロー中・解除",
                                followButtonEnabled = true,
                                onClickFollow = {},
                                listener = null,
                            ),
                            HomeScreenUiState.SearchAccount(
                                iconUrl = null,
                                displayName = "Compose News",
                                acct = "@compose@remote.example",
                                followButtonText = "フォロー",
                                followButtonEnabled = true,
                                onClickFollow = {},
                                listener = null,
                            ),
                        ),
                        loadMoreButtonVisible = true,
                    ),
                ),
                profile = HomeScreenUiState.Profile.Loading,
                following = HomeScreenUiState.Following.Loading,
                userTabIconUrl = null,
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun HomeSearchEmptyContentPreview() {
    HomePreviewTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Search,
                timelineItems = listOf(),
                timelineRefreshing = false,
                search = HomeScreenUiState.Search(
                    query = "unknown",
                    result = HomeScreenUiState.SearchResult.Loaded(
                        accounts = listOf(),
                        loadMoreButtonVisible = false,
                    ),
                ),
                profile = HomeScreenUiState.Profile.Loading,
                following = HomeScreenUiState.Following.Loading,
                userTabIconUrl = null,
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun HomeUserContentPreview() {
    HomePreviewTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.User,
                timelineItems = listOf(),
                timelineRefreshing = false,
                search = HomeScreenUiState.Search(
                    query = "",
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loaded(
                    iconUrl = "https://example.com/icon.png",
                    displayName = "ユーザー1",
                    acct = "@user1",
                ),
                following = HomeScreenUiState.Following.Loaded(
                    accounts = listOf(
                        HomeScreenUiState.SearchAccount(
                            iconUrl = "https://example.com/icon.png",
                            displayName = "Kotlin Weekly",
                            acct = "@kotlinweekly@remote.example",
                            followButtonText = "フォロー中・解除",
                            followButtonEnabled = true,
                            onClickFollow = {},
                            listener = null,
                        ),
                        HomeScreenUiState.SearchAccount(
                            iconUrl = null,
                            displayName = "https://other.example/users/compose",
                            acct = "other.example",
                            followButtonText = "フォロー",
                            followButtonEnabled = true,
                            onClickFollow = {},
                            listener = null,
                        ),
                    ),
                    loadMoreButtonVisible = true,
                ),
                userTabIconUrl = "https://example.com/icon.png",
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun HomePreviewTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { ColorImage(0xFF26A69A.toInt()) },
    ) {
        MaterialTheme(content = content)
    }
}

private object AndroidPreviewHomeListener : HomeScreenUiState.Listener {
    override fun onClickPost() = Unit

    override fun onClickSettings() = Unit

    override fun onClickTab(tab: HomeScreenUiState.Tab) = Unit

    override fun onTabChanged(tab: HomeScreenUiState.Tab) = Unit

    override fun onRefreshTimeline() = Unit

    override fun onSearchQueryChanged(query: String) = Unit

    override fun onSearch() = Unit

    override fun onClickLoadMoreSearchResult() = Unit

    override fun onClickEditProfile() = Unit

    override fun onClickRetryProfile() = Unit

    override fun onClickLoadMoreFollowing() = Unit

    override fun onClickRetryFollowing() = Unit

    override fun onScreenShown() = Unit
}

private object AndroidPreviewSearchAccountListener : HomeScreenUiState.SearchAccountListener {
    override fun onClick() = Unit
}
