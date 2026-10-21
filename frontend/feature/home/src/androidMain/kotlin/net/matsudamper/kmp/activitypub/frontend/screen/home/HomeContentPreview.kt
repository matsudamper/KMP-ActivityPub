package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.material3.MaterialTheme
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
                search = HomeScreenUiState.Search(
                    query = "",
                    accounts = listOf(),
                ),
                listener = AndroidPreviewHomeListener,
            ),
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
                search = HomeScreenUiState.Search(
                    query = "kotlin",
                    accounts = listOf(
                        HomeScreenUiState.SearchAccount(
                            displayName = "Kotlin",
                            acct = "@kotlin@example.com",
                        ),
                        HomeScreenUiState.SearchAccount(
                            displayName = "Kotlin Weekly",
                            acct = "@kotlinweekly@example.com",
                        ),
                    ),
                ),
                listener = AndroidPreviewHomeListener,
            ),
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
                search = HomeScreenUiState.Search(
                    query = "",
                    accounts = listOf(),
                ),
                listener = AndroidPreviewHomeListener,
            ),
        )
    }
}

private object AndroidPreviewHomeListener : HomeScreenUiState.Listener {
    override fun onClickPost() = Unit

    override fun onClickSettings() = Unit

    override fun onClickTab(tab: HomeScreenUiState.Tab) = Unit

    override fun onSearchQueryChanged(query: String) = Unit

    override fun onSearch() = Unit
}
