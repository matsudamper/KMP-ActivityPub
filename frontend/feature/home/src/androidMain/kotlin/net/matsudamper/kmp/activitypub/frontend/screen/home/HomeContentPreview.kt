package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

@Preview
@Composable
private fun HomeContentPreview() = HomeTimelinePreview(HomeScreenUiState.TimelineFooter.None, streamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving)

@Preview
@Composable
private fun HomeContentPreviewLoading() = HomeTimelinePreview(HomeScreenUiState.TimelineFooter.Loading, streamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving)

@Preview
@Composable
private fun HomeContentPreviewError() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.Error("続きを読み込めませんでした"),
    streamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
)

@Preview
@Composable
private fun HomeContentPreviewStreamDisconnected() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.None,
    streamStatus = HomeScreenUiState.TimelineStreamStatus.Disconnected,
)

@Preview
@Composable
private fun HomeContentPreviewStreamWaitingToReconnect() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.None,
    streamStatus = HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect,
)

@Composable
private fun HomeTimelinePreview(
    footer: HomeScreenUiState.TimelineFooter,
    streamStatus: HomeScreenUiState.TimelineStreamStatus,
) {
    HomePreviewTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Home,
                timelineItems = listOf(
                    PostItemUiState(
                        iconUrl = "https://example.com/kotlin.png",
                        displayName = "Kotlin",
                        acct = "@kotlin@example.com",
                        body = createPostBodyText(content = "Kotlin 2.4 がリリースされました https://kotlinlang.org/", onOpenUrl = {}),
                        postedAt = "1分前",
                        linkPreviews = listOf(
                            PostItemUiState.LinkPreview(
                                title = "Kotlin Programming Language",
                                siteName = "Kotlin",
                                imageUrl = "https://example.com/ogp.png",
                                listener = AndroidPreviewLinkPreviewListener,
                            ),
                            PostItemUiState.LinkPreview(
                                title = "https://example.com/not-loaded",
                                siteName = "example.com",
                                imageUrl = null,
                                listener = AndroidPreviewLinkPreviewListener,
                            ),
                        ),
                        images = listOf(
                            PostItemUiState.Image(
                                url = "https://example.com/photo.png",
                                description = "写真",
                                blurToggleButtonVisible = false,
                                blurred = false,
                                listener = AndroidPreviewImageListener,
                            ),
                        ),
                        favouriteCountText = null,
                        stamps = listOf(),
                        authorListener = AndroidPreviewAuthorListener,
                        listener = AndroidPreviewTimelineItemListener,
                    ),
                    PostItemUiState(
                        iconUrl = null,
                        displayName = "Compose",
                        acct = "@compose@example.com",
                        body = createPostBodyText(content = "Compose Multiplatform で Web と Android の画面を共有している", onOpenUrl = {}),
                        postedAt = "5分前",
                        linkPreviews = listOf(),
                        images = listOf(
                            PostItemUiState.Image(
                                url = "https://example.com/1.png",
                                description = null,
                                blurToggleButtonVisible = true,
                                blurred = true,
                                listener = AndroidPreviewImageListener,
                            ),
                            PostItemUiState.Image(
                                url = "https://example.com/2.png",
                                description = null,
                                blurToggleButtonVisible = true,
                                blurred = true,
                                listener = AndroidPreviewImageListener,
                            ),
                            PostItemUiState.Image(
                                url = "https://example.com/3.png",
                                description = null,
                                blurToggleButtonVisible = true,
                                blurred = true,
                                listener = AndroidPreviewImageListener,
                            ),
                        ),
                        favouriteCountText = "3",
                        stamps = listOf(
                            PostItemUiState.Stamp(emoji = "👍", imageUrl = null, countText = "5"),
                            PostItemUiState.Stamp(emoji = "🎉", imageUrl = null, countText = "2"),
                            PostItemUiState.Stamp(emoji = ":kotlin:", imageUrl = "https://example.com/kotlin.png", countText = "1"),
                        ),
                        authorListener = AndroidPreviewAuthorListener,
                        listener = AndroidPreviewTimelineItemListener,
                    ),
                ),
                timelineRefreshing = false,
                timelineFooter = footer,
                timelineStreamStatus = streamStatus,
                sensitiveImagesBlurred = true,
                search = HomeScreenUiState.Search(
                    query = "",
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                openedColumns = listOf(),
                railItems = listOf(),
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
            tabListStates = rememberHomeTabListStates(),
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
                timelineFooter = HomeScreenUiState.TimelineFooter.None,
                timelineStreamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
                sensitiveImagesBlurred = true,
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
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                openedColumns = listOf(),
                railItems = listOf(),
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
            tabListStates = rememberHomeTabListStates(),
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
                timelineFooter = HomeScreenUiState.TimelineFooter.None,
                timelineStreamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
                sensitiveImagesBlurred = true,
                search = HomeScreenUiState.Search(
                    query = "unknown",
                    result = HomeScreenUiState.SearchResult.Loaded(
                        accounts = listOf(),
                        loadMoreButtonVisible = false,
                    ),
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                openedColumns = listOf(),
                railItems = listOf(),
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
            tabListStates = rememberHomeTabListStates(),
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
                timelineFooter = HomeScreenUiState.TimelineFooter.None,
                timelineStreamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
                sensitiveImagesBlurred = true,
                search = HomeScreenUiState.Search(
                    query = "",
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loaded(
                    iconUrl = "https://example.com/icon.png",
                    displayName = "ユーザー1",
                    acct = "@user1",
                    followingCount = "12",
                    followersCount = "3",
                ),
                userPosts = HomeScreenUiState.UserPosts.Loaded(
                    items = listOf(
                        PostItemUiState(
                            iconUrl = "https://example.com/icon.png",
                            displayName = "ユーザー1",
                            acct = "@user1",
                            body = createPostBodyText(content = "はじめての投稿", onOpenUrl = {}),
                            postedAt = "3分前",
                            linkPreviews = listOf(),
                            images = listOf(),
                            favouriteCountText = null,
                            stamps = listOf(),
                            authorListener = null,
                            listener = AndroidPreviewTimelineItemListener,
                        ),
                    ),
                    emptyMessage = null,
                ),
                userTabIconUrl = "https://example.com/icon.png",
                openedColumns = listOf(),
                railItems = listOf(),
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
            tabListStates = rememberHomeTabListStates(),
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun HomeColumnsContentPreview() {
    HomePreviewTheme {
        HomeColumnsContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Home,
                timelineItems = listOf(
                    PostItemUiState(
                        iconUrl = "https://example.com/kotlin.png",
                        displayName = "Kotlin",
                        acct = "@kotlin@example.com",
                        body = createPostBodyText(content = "Kotlin 2.4 がリリースされました https://kotlinlang.org/", onOpenUrl = {}),
                        postedAt = "1分前",
                        linkPreviews = listOf(),
                        images = listOf(),
                        favouriteCountText = null,
                        stamps = listOf(),
                        authorListener = AndroidPreviewAuthorListener,
                        listener = AndroidPreviewTimelineItemListener,
                    ),
                ),
                timelineRefreshing = false,
                timelineFooter = HomeScreenUiState.TimelineFooter.None,
                timelineStreamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
                sensitiveImagesBlurred = true,
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
                        ),
                        loadMoreButtonVisible = false,
                    ),
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                openedColumns = listOf(HomeScreenUiState.Column.Post, HomeScreenUiState.Column.Search),
                railItems = listOf(
                    HomeScreenUiState.RailItem(column = HomeScreenUiState.Column.Post, selected = true),
                    HomeScreenUiState.RailItem(column = HomeScreenUiState.Column.Search, selected = true),
                    HomeScreenUiState.RailItem(column = HomeScreenUiState.Column.User, selected = false),
                ),
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
            tabListStates = rememberHomeTabListStates(),
            postColumn = { modifier ->
                Box(modifier = modifier, contentAlignment = Alignment.Center) {
                    Text("投稿")
                }
            },
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

    override fun onClickRailItem(column: HomeScreenUiState.Column) = Unit

    override fun onClickCloseColumn(column: HomeScreenUiState.Column) = Unit

    override fun onTabChanged(tab: HomeScreenUiState.Tab) = Unit

    override fun onRefreshTimeline() = Unit
    override fun onScrolledToTimelineEnd() = Unit
    override fun onClickRetryTimelineLoadMore() = Unit

    override fun onClickTimelineStreamStatus() = Unit

    override fun onClickSensitiveImagesVisibility() = Unit

    override fun onSearchQueryChanged(query: String) = Unit

    override fun onSearch() = Unit

    override fun onClickLoadMoreSearchResult() = Unit

    override fun onClickEditProfile() = Unit

    override fun onClickRetryProfile() = Unit

    override fun onClickRetryUserPosts() = Unit

    override fun onClickFollowing() = Unit

    override fun onClickFollowers() = Unit

    override fun onScreenShown() = Unit
}

private object AndroidPreviewTimelineItemListener : PostItemUiState.Listener {
    override fun onVisible() = Unit
}

private object AndroidPreviewAuthorListener : PostItemUiState.AuthorListener {
    override fun onClick() = Unit
}

private object AndroidPreviewLinkPreviewListener : PostItemUiState.LinkPreviewListener {
    override fun onClick() = Unit
}

private object AndroidPreviewImageListener : PostItemUiState.ImageListener {
    override fun onClick() = Unit

    override fun onClickBlurToggle() = Unit
}

private object AndroidPreviewSearchAccountListener : HomeScreenUiState.SearchAccountListener {
    override fun onClick() = Unit
}
