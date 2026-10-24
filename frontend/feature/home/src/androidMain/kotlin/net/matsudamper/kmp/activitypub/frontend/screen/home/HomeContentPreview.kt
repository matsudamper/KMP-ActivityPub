package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun HomeContentPreview() {
    MaterialTheme {
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
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun HomeSearchContentPreview() {
    MaterialTheme {
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
                                avatarInitial = "K",
                                displayName = "kotlin",
                                acct = "@kotlin",
                                listener = AndroidPreviewSearchAccountListener,
                            ),
                            HomeScreenUiState.SearchAccount(
                                avatarInitial = "K",
                                displayName = "Kotlin Weekly",
                                acct = "@kotlinweekly@remote.example",
                                listener = null,
                            ),
                        ),
                        loadMoreButtonVisible = true,
                    ),
                ),
                profile = HomeScreenUiState.Profile.Loading,
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun HomeSearchEmptyContentPreview() {
    MaterialTheme {
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
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun HomeUserContentPreview() {
    MaterialTheme {
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
                    avatarInitial = "U",
                    displayName = "ユーザー1",
                    acct = "@user1",
                ),
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private object AndroidPreviewHomeListener : HomeScreenUiState.Listener {
    override fun onClickPost() = Unit

    override fun onClickSettings() = Unit

    override fun onClickTab(tab: HomeScreenUiState.Tab) = Unit

    override fun onRefreshTimeline() = Unit

    override fun onSearchQueryChanged(query: String) = Unit

    override fun onSearch() = Unit

    override fun onClickLoadMoreSearchResult() = Unit

    override fun onClickEditProfile() = Unit

    override fun onClickRetryProfile() = Unit

    override fun onScreenShown() = Unit
}

private object AndroidPreviewSearchAccountListener : HomeScreenUiState.SearchAccountListener {
    override fun onClick() = Unit
}
