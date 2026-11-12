package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserCustomEmoji
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.screen.createTextWithEmojis
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostContent
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostNavigationIcon
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreenUiState
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile
import net.matsudamper.kmp.activitypub.frontend.ui.PostItem
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemStyle
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinkUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaUiState
import net.matsudamper.kmp.activitypub.frontend.ui.previewFollowMenu

@Preview
@Composable
private fun HomeContentPreview() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.None,
    streamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
    deleteNoteDialog = null,
    timelineColumnsEmptyMessage = null,
    timelineColumnEditDialog = null,
    timelineColumnDeleteDialog = null,
)

@Preview
@Composable
private fun HomeContentPreviewLoading() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.Loading,
    streamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
    deleteNoteDialog = null,
    timelineColumnsEmptyMessage = null,
    timelineColumnEditDialog = null,
    timelineColumnDeleteDialog = null,
)

@Preview
@Composable
private fun HomeContentPreviewError() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.Error("続きを読み込めませんでした"),
    streamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
    deleteNoteDialog = null,
    timelineColumnsEmptyMessage = null,
    timelineColumnEditDialog = null,
    timelineColumnDeleteDialog = null,
)

@Preview
@Composable
private fun HomeContentPreviewStreamDisconnected() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.None,
    streamStatus = HomeScreenUiState.TimelineStreamStatus.Disconnected,
    deleteNoteDialog = null,
    timelineColumnsEmptyMessage = null,
    timelineColumnEditDialog = null,
    timelineColumnDeleteDialog = null,
)

@Preview
@Composable
private fun HomeContentPreviewDeleteNoteDialog() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.None,
    streamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
    deleteNoteDialog = DeleteNoteDialogUiState(deleteButtonEnabled = true, listener = AndroidPreviewDeleteNoteDialogListener),
    timelineColumnsEmptyMessage = null,
    timelineColumnEditDialog = null,
    timelineColumnDeleteDialog = null,
)

@Preview
@Composable
private fun HomeContentPreviewStreamWaitingToReconnect() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.None,
    streamStatus = HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect,
    deleteNoteDialog = null,
    timelineColumnsEmptyMessage = null,
    timelineColumnEditDialog = null,
    timelineColumnDeleteDialog = null,
)

@Preview
@Composable
private fun HomeContentPreviewTimelineColumnsEmpty() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.None,
    streamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
    deleteNoteDialog = null,
    timelineColumnsEmptyMessage = "表示するカラムがありません",
    timelineColumnEditDialog = null,
    timelineColumnDeleteDialog = null,
)

@Preview
@Composable
private fun TimelineTabsMenuItemsPreview() {
    MaterialTheme {
        Surface(tonalElevation = 3.dp, shadowElevation = 3.dp) {
            TimelineTabsMenuItems(
                selectedColumn = previewTimelineColumns(items = listOf(), footer = HomeScreenUiState.TimelineFooter.None).first(),
                addItemEnabled = true,
                onClickAdd = {},
                onItemClick = {},
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
}

@Preview
@Composable
private fun HomeContentPreviewTimelineColumnEditDialog() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.None,
    streamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
    deleteNoteDialog = null,
    timelineColumnsEmptyMessage = null,
    timelineColumnEditDialog = HomeScreenUiState.TimelineColumnEditDialog(
        title = "カラムを編集",
        name = "技術と友達",
        allSourcesCheckState = ToggleableState.Indeterminate,
        sources = listOf(
            HomeScreenUiState.TimelineColumnEditDialog.Source(name = "メイン", checked = false, listener = AndroidPreviewTimelineColumnEditDialogSourceListener),
            HomeScreenUiState.TimelineColumnEditDialog.Source(name = "技術", checked = true, listener = AndroidPreviewTimelineColumnEditDialogSourceListener),
            HomeScreenUiState.TimelineColumnEditDialog.Source(name = "友達", checked = true, listener = AndroidPreviewTimelineColumnEditDialogSourceListener),
        ),
        saveButtonEnabled = true,
        listener = AndroidPreviewTimelineColumnEditDialogListener,
    ),
    timelineColumnDeleteDialog = null,
)

@Preview
@Composable
private fun HomeContentPreviewTimelineColumnDeleteDialog() = HomeTimelinePreview(
    HomeScreenUiState.TimelineFooter.None,
    streamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
    deleteNoteDialog = null,
    timelineColumnsEmptyMessage = null,
    timelineColumnEditDialog = null,
    timelineColumnDeleteDialog = HomeScreenUiState.TimelineColumnDeleteDialog(
        message = "「技術と友達」のカラムを削除する",
        listener = AndroidPreviewTimelineColumnDeleteDialogListener,
    ),
)

@Preview
@Composable
private fun TimelineHeaderDividerNewPostsHiddenAbovePreview() {
    HomePreviewTheme {
        Surface {
            Column {
                Text(text = "先頭にいる", modifier = Modifier.padding(16.dp))
                TimelineHeaderDivider(newPostsHiddenAbove = false)
                PostItem(uiState = previewTimelinePosts().first(), style = PostItemStyle.Timeline)
                Text(text = "先頭から離れている", modifier = Modifier.padding(16.dp))
                TimelineHeaderDivider(newPostsHiddenAbove = true)
                PostItem(uiState = previewTimelinePosts().first(), style = PostItemStyle.Timeline)
            }
        }
    }
}

@Preview
@Composable
private fun TimelineNotificationAndDeletedItemsPreview() {
    HomePreviewTheme {
        Surface {
            Column {
                val posts = previewTimelinePosts()
                val notifiedPost = posts.first()
                listOf(
                    notifiedPost.copy(
                        actorLabel = PostItemUiState.ActorLabel(
                            icon = PostItemUiState.ActorLabelIcon.Favourite,
                            text = createTextWithEmojis(text = "Alice :kotlin: がお気に入り", emojis = PreviewEmojis),
                            iconUrl = null,
                            cancelledLabelVisible = false,
                            listener = AndroidPreviewActorLabelListener,
                        ),
                    ),
                    notifiedPost.copy(
                        actorLabel = PostItemUiState.ActorLabel(
                            icon = PostItemUiState.ActorLabelIcon.Boost,
                            text = createTextWithEmojis(text = "Bob がブースト", emojis = listOf()),
                            iconUrl = null,
                            cancelledLabelVisible = false,
                            listener = AndroidPreviewActorLabelListener,
                        ),
                    ),
                    notifiedPost.copy(deletedLabelVisible = true),
                    posts[1].copy(deletedLabelVisible = true),
                ).forEach { item ->
                    PostItem(uiState = item, style = PostItemStyle.Timeline)
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * @param timelineColumnsEmptyMessage null でなければカラムを全部消した状態にする
 */
@Composable
private fun HomeTimelinePreview(
    footer: HomeScreenUiState.TimelineFooter,
    streamStatus: HomeScreenUiState.TimelineStreamStatus,
    deleteNoteDialog: DeleteNoteDialogUiState?,
    timelineColumnsEmptyMessage: String?,
    timelineColumnEditDialog: HomeScreenUiState.TimelineColumnEditDialog?,
    timelineColumnDeleteDialog: HomeScreenUiState.TimelineColumnDeleteDialog?,
) {
    HomePreviewTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Home,
                timelineColumns = if (timelineColumnsEmptyMessage == null) previewTimelineColumns(previewTimelinePosts(), footer) else listOf(),
                timelineColumnsEmptyMessage = timelineColumnsEmptyMessage,
                addTimelineColumnButtonEnabled = true,
                timelineColumnEditDialog = timelineColumnEditDialog,
                timelineColumnDeleteDialog = timelineColumnDeleteDialog,
                timelineRefreshing = false,
                timelineStreamStatus = streamStatus,
                sensitiveImagesBlurred = true,
                search = HomeScreenUiState.Search(
                    query = "",
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userContentTab = ProfileContentTab.Posts,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                openedColumn = null,
                railItems = listOf(),
                deleteNoteDialog = deleteNoteDialog,
                addFollowingGroupDialog = null,
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
                timelineColumns = listOf(),
                timelineColumnsEmptyMessage = null,
                addTimelineColumnButtonEnabled = true,
                timelineColumnEditDialog = null,
                timelineColumnDeleteDialog = null,
                timelineRefreshing = false,
                timelineStreamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
                sensitiveImagesBlurred = true,
                search = HomeScreenUiState.Search(
                    query = "kotlin",
                    result = HomeScreenUiState.SearchResult.Loaded(
                        accounts = listOf(
                            HomeScreenUiState.SearchAccount(
                                iconUrl = "https://example.com/icon.png",
                                displayName = createTextWithEmojis(text = "kotlin", emojis = listOf()),
                                acct = "@kotlin",
                                followMenu = null,
                                listener = AndroidPreviewSearchAccountListener,
                            ),
                            HomeScreenUiState.SearchAccount(
                                iconUrl = null,
                                displayName = createTextWithEmojis(text = "Kotlin Weekly :kotlin:", emojis = PreviewEmojis),
                                acct = "@kotlinweekly@remote.example",
                                followMenu = previewFollowMenu(destinationName = "ニュース"),
                                listener = null,
                            ),
                            HomeScreenUiState.SearchAccount(
                                iconUrl = null,
                                displayName = createTextWithEmojis(text = "Compose News", emojis = listOf()),
                                acct = "@compose@remote.example",
                                followMenu = previewFollowMenu(destinationName = null),
                                listener = null,
                            ),
                        ),
                        loadMoreButtonVisible = true,
                    ),
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userContentTab = ProfileContentTab.Posts,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                openedColumn = null,
                railItems = listOf(),
                deleteNoteDialog = null,
                addFollowingGroupDialog = null,
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
                timelineColumns = listOf(),
                timelineColumnsEmptyMessage = null,
                addTimelineColumnButtonEnabled = true,
                timelineColumnEditDialog = null,
                timelineColumnDeleteDialog = null,
                timelineRefreshing = false,
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
                userContentTab = ProfileContentTab.Posts,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                openedColumn = null,
                railItems = listOf(),
                deleteNoteDialog = null,
                addFollowingGroupDialog = null,
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
                timelineColumns = listOf(),
                timelineColumnsEmptyMessage = null,
                addTimelineColumnButtonEnabled = true,
                timelineColumnEditDialog = null,
                timelineColumnDeleteDialog = null,
                timelineRefreshing = false,
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
                    summary = "Kotlin で ActivityPub サーバーを作っています。",
                    links = listOf(
                        ProfileLinkUiState(name = "GitHub", url = "https://github.com/user1", listener = AndroidPreviewProfileLinkListener),
                    ),
                    followingCount = "12",
                    followersCount = "3",
                ),
                userContentTab = ProfileContentTab.Posts,
                userPosts = HomeScreenUiState.UserPosts.Loaded(
                    items = listOf(
                        PostItemUiState(
                            pinnedLabelVisible = false,
                            deletedLabelVisible = false,
                            actorLabel = null,
                            iconUrl = "https://example.com/icon.png",
                            displayName = createTextWithEmojis(text = "ユーザー1", emojis = listOf()),
                            acct = "@user1",
                            body = createPostBodyText(content = "はじめての投稿", emojis = listOf(), onOpenUrl = {}),
                            postedAt = "3分前",
                            fullPostedAt = "2026/11/05 12:00",
                            linkPreviews = listOf(),
                            images = listOf(),
                            favouriteCountText = null,
                            boostCountText = null,
                            favouriteButton = null,
                            boostButton = null,
                            stamps = listOf(),
                            authorListener = null,
                            menuItems = listOf(),
                            postListener = null,
                            shareListener = AndroidPreviewShareListener,
                            listener = AndroidPreviewTimelineItemListener,
                        ),
                    ),
                    emptyMessage = null,
                    media = listOf(),
                    mediaEmptyMessage = "表示できるメディアはありません",
                ),
                userTabIconUrl = "https://example.com/icon.png",
                openedColumn = null,
                railItems = listOf(),
                deleteNoteDialog = null,
                addFollowingGroupDialog = null,
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
            tabListStates = rememberHomeTabListStates(),
        )
    }
}

@Preview
@Composable
private fun HomeUserMediaEmptyContentPreview() {
    HomePreviewTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.User,
                timelineColumns = listOf(),
                timelineColumnsEmptyMessage = null,
                addTimelineColumnButtonEnabled = true,
                timelineColumnEditDialog = null,
                timelineColumnDeleteDialog = null,
                timelineRefreshing = false,
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
                    summary = "Kotlin で ActivityPub サーバーを作っています。",
                    links = listOf(
                        ProfileLinkUiState(name = "GitHub", url = "https://github.com/user1", listener = AndroidPreviewProfileLinkListener),
                    ),
                    followingCount = "12",
                    followersCount = "3",
                ),
                userContentTab = ProfileContentTab.Media,
                userPosts = HomeScreenUiState.UserPosts.Loaded(
                    items = listOf(),
                    emptyMessage = "表示できる投稿はありません",
                    media = listOf(),
                    mediaEmptyMessage = "表示できるメディアはありません",
                ),
                userTabIconUrl = "https://example.com/icon.png",
                openedColumn = null,
                railItems = listOf(),
                deleteNoteDialog = null,
                addFollowingGroupDialog = null,
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
            tabListStates = rememberHomeTabListStates(),
        )
    }
}

@Preview
@Composable
private fun HomeUserMediaContentPreview() {
    HomePreviewTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.User,
                timelineColumns = listOf(),
                timelineColumnsEmptyMessage = null,
                addTimelineColumnButtonEnabled = true,
                timelineColumnEditDialog = null,
                timelineColumnDeleteDialog = null,
                timelineRefreshing = false,
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
                    summary = null,
                    links = listOf(),
                    followingCount = "12",
                    followersCount = "3",
                ),
                userContentTab = ProfileContentTab.Media,
                userPosts = HomeScreenUiState.UserPosts.Loaded(
                    items = listOf(
                        PostItemUiState(
                            pinnedLabelVisible = false,
                            deletedLabelVisible = false,
                            actorLabel = null,
                            iconUrl = "https://example.com/icon.png",
                            displayName = createTextWithEmojis(text = "ユーザー1", emojis = listOf()),
                            acct = "@user1",
                            body = createPostBodyText(content = "はじめての投稿", emojis = listOf(), onOpenUrl = {}),
                            postedAt = "3分前",
                            fullPostedAt = "2026/11/05 12:00",
                            linkPreviews = listOf(),
                            images = listOf(),
                            favouriteCountText = null,
                            boostCountText = null,
                            favouriteButton = null,
                            boostButton = null,
                            stamps = listOf(),
                            authorListener = null,
                            menuItems = listOf(),
                            postListener = null,
                            shareListener = AndroidPreviewShareListener,
                            listener = AndroidPreviewTimelineItemListener,
                        ),
                    ),
                    emptyMessage = null,
                    media = List(5) { index ->
                        ProfileMediaUiState(
                            url = "https://example.com/media/$index.png",
                            description = null,
                            blurred = index == 2,
                            listener = AndroidPreviewProfileMediaListener,
                        )
                    },
                    mediaEmptyMessage = null,
                ),
                userTabIconUrl = "https://example.com/icon.png",
                openedColumn = null,
                railItems = listOf(),
                deleteNoteDialog = null,
                addFollowingGroupDialog = null,
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
            tabListStates = rememberHomeTabListStates(),
        )
    }
}

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun HomeColumnsContentPreview() = HomeColumnsPreview(openedColumn = HomeScreenUiState.Column.Post, timelineColumnsEmptyMessage = null)

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun HomeColumnsContentPreviewTimelineColumns() = HomeColumnsPreview(openedColumn = null, timelineColumnsEmptyMessage = null)

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun HomeColumnsContentPreviewTimelineColumnsEmpty() = HomeColumnsPreview(openedColumn = null, timelineColumnsEmptyMessage = "表示するカラムがありません")

/**
 * @param timelineColumnsEmptyMessage null でなければカラムを全部消した状態にする
 */
@Composable
private fun HomeColumnsPreview(
    openedColumn: HomeScreenUiState.Column?,
    timelineColumnsEmptyMessage: String?,
) {
    HomePreviewTheme {
        HomeColumnsContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Home,
                timelineColumns = if (timelineColumnsEmptyMessage == null) previewTimelineColumns(previewColumnsPosts(), HomeScreenUiState.TimelineFooter.None) else listOf(),
                timelineColumnsEmptyMessage = timelineColumnsEmptyMessage,
                addTimelineColumnButtonEnabled = true,
                timelineColumnEditDialog = null,
                timelineColumnDeleteDialog = null,
                timelineRefreshing = false,
                timelineStreamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
                sensitiveImagesBlurred = true,
                search = HomeScreenUiState.Search(
                    query = "kotlin",
                    result = HomeScreenUiState.SearchResult.Loaded(
                        accounts = listOf(
                            HomeScreenUiState.SearchAccount(
                                iconUrl = "https://example.com/icon.png",
                                displayName = createTextWithEmojis(text = "kotlin", emojis = listOf()),
                                acct = "@kotlin",
                                followMenu = null,
                                listener = AndroidPreviewSearchAccountListener,
                            ),
                        ),
                        loadMoreButtonVisible = false,
                    ),
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userContentTab = ProfileContentTab.Posts,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                deleteNoteDialog = null,
                addFollowingGroupDialog = null,
                openedColumn = openedColumn,
                railItems = listOf(
                    HomeScreenUiState.RailItem(column = HomeScreenUiState.Column.Post, selected = openedColumn == HomeScreenUiState.Column.Post),
                    HomeScreenUiState.RailItem(column = HomeScreenUiState.Column.Search, selected = false),
                    HomeScreenUiState.RailItem(column = HomeScreenUiState.Column.User, selected = false),
                ),
                listener = AndroidPreviewHomeListener,
            ),
            snackbarHostState = SnackbarHostState(),
            tabListStates = rememberHomeTabListStates(),
            postColumn = { modifier ->
                Box(modifier = modifier) {
                    PostContent(
                        uiState = PostScreenUiState(
                            body = "",
                            inputHasError = false,
                            inputEnabled = true,
                            backButtonEnabled = true,
                            postButtonEnabled = false,
                            images = listOf(),
                            addImageButtonEnabled = true,
                            addImageLoadingVisible = false,
                            listener = AndroidPreviewPostScreenListener,
                        ),
                        navigationIcon = PostNavigationIcon.Close,
                        snackbarHostState = SnackbarHostState(),
                    )
                }
            },
        )
    }
}

@OptIn(ExperimentalCoilApi::class)
private val PreviewEmojis = listOf(
    UserCustomEmoji(name = ":kotlin:", imageUrl = "https://example.com/kotlin.png"),
    UserCustomEmoji(name = ":compose:", imageUrl = "https://example.com/compose.png"),
)

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

    override fun onClickColumnHeader(tab: HomeScreenUiState.Tab) = Unit

    override fun onPostColumnFinished() = Unit

    override fun onTabChanged(tab: HomeScreenUiState.Tab) = Unit

    override fun onRefreshTimeline() = Unit

    override fun onClickAddTimelineColumn() = Unit

    override fun onMoveTimelineColumn(fromIndex: Int, toIndex: Int) = Unit

    override fun onClickTimelineStreamStatus() = Unit

    override fun onClickSensitiveImagesVisibility() = Unit

    override fun onSearchQueryChanged(query: String) = Unit

    override fun onSearch() = Unit

    override fun onClickLoadMoreSearchResult() = Unit

    override fun onClickEditProfile() = Unit

    override fun onClickRetryProfile() = Unit

    override fun onClickRetryUserPosts() = Unit

    override fun onClickUserContentTab(tab: ProfileContentTab) = Unit

    override fun onClickFollowing() = Unit

    override fun onClickFollowers() = Unit

    override fun onScreenShown() = Unit
}

private object AndroidPreviewDeleteNoteDialogListener : DeleteNoteDialogUiState.Listener {
    override fun onClickDelete() = Unit

    override fun onDismiss() = Unit
}

private object AndroidPreviewProfileMediaListener : ProfileMediaUiState.Listener {
    override fun onClick() = Unit
}

private object AndroidPreviewTimelineItemListener : PostItemUiState.Listener {
    override fun onVisible() = Unit
}

private object AndroidPreviewAuthorListener : PostItemUiState.AuthorListener {
    override fun onClick() = Unit
}

private object AndroidPreviewActorLabelListener : PostItemUiState.ActorLabelListener {
    override fun onClick() = Unit
}

private object AndroidPreviewPostListener : PostItemUiState.PostListener {
    override fun onClick() = Unit
}

private object AndroidPreviewShareListener : PostItemUiState.ShareListener {
    override fun onClick() = Unit
}

private object AndroidPreviewFavouriteButtonListener : PostItemUiState.FavouriteButtonListener {
    override fun onClick() = Unit
}

private object AndroidPreviewBoostButtonListener : PostItemUiState.BoostButtonListener {
    override fun onClick() = Unit
}

private object AndroidPreviewLinkPreviewListener : PostItemUiState.LinkPreviewListener {
    override fun onClick() = Unit
}

private object AndroidPreviewMenuItemListener : PostItemUiState.MenuItemListener {
    override fun onClick() = Unit
}

private object AndroidPreviewImageListener : PostItemUiState.ImageListener {
    override fun onClick() = Unit

    override fun onClickBlurToggle() = Unit
}

private object AndroidPreviewSearchAccountListener : HomeScreenUiState.SearchAccountListener {
    override fun onClick() = Unit
}

private object AndroidPreviewProfileLinkListener : ProfileLinkUiState.Listener {
    override fun onClick() = Unit
}

private object AndroidPreviewPostScreenListener : PostScreenUiState.Listener {
    override fun onBodyChanged(text: String) = Unit

    override fun onClickAddImage() = Unit

    override fun onImagePicked(file: PickedImageFile) = Unit

    override fun onClickPost() = Unit

    override fun onPressPostShortcut() = Unit

    override fun onClickBack() = Unit
}

private fun previewTimelinePosts(): List<PostItemUiState> = listOf(
    PostItemUiState(
        pinnedLabelVisible = false,
        deletedLabelVisible = false,
        actorLabel = null,
        iconUrl = "https://example.com/kotlin.png",
        displayName = createTextWithEmojis(text = "Kotlin", emojis = listOf()),
        acct = "@kotlin@example.com",
        body = createPostBodyText(content = "Kotlin 2.4 がリリースされました https://kotlinlang.org/", emojis = listOf(), onOpenUrl = {}),
        postedAt = "1分前",
        fullPostedAt = "2026/11/05 12:00",
        linkPreviews = listOf(
            PostItemUiState.LinkPreview(
                title = "Kotlin Programming Language",
                siteName = "Kotlin",
                imageUrl = "https://example.com/ogp.png",
                faviconUrl = "https://example.com/favicon.ico",
                listener = AndroidPreviewLinkPreviewListener,
            ),
            PostItemUiState.LinkPreview(
                title = "https://example.com/not-loaded",
                siteName = "example.com",
                imageUrl = null,
                faviconUrl = null,
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
        boostCountText = null,
        favouriteButton = null,
        boostButton = PostItemUiState.BoostButton(
            iconHighlighted = true,
            listener = AndroidPreviewBoostButtonListener,
        ),
        stamps = listOf(),
        authorListener = AndroidPreviewAuthorListener,
        menuItems = listOf(),
        postListener = AndroidPreviewPostListener,
        shareListener = AndroidPreviewShareListener,
        listener = AndroidPreviewTimelineItemListener,
    ),
    PostItemUiState(
        pinnedLabelVisible = false,
        deletedLabelVisible = false,
        actorLabel = PostItemUiState.ActorLabel(
            icon = PostItemUiState.ActorLabelIcon.Boost,
            text = createTextWithEmojis(text = "Alice :kotlin: がブースト", emojis = PreviewEmojis),
            iconUrl = null,
            cancelledLabelVisible = true,
            listener = AndroidPreviewActorLabelListener,
        ),
        iconUrl = null,
        displayName = createTextWithEmojis(text = "Compose :compose:", emojis = PreviewEmojis),
        acct = "@compose@example.com",
        body = createPostBodyText(content = "Compose Multiplatform :compose: で Web と Android の画面を共有している :kotlin:", emojis = PreviewEmojis, onOpenUrl = {}),
        postedAt = "5分前",
        fullPostedAt = "2026/11/05 12:00",
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
        boostCountText = null,
        favouriteButton = PostItemUiState.FavouriteButton(
            iconHighlighted = true,
            listener = AndroidPreviewFavouriteButtonListener,
        ),
        boostButton = PostItemUiState.BoostButton(
            iconHighlighted = false,
            listener = AndroidPreviewBoostButtonListener,
        ),
        stamps = listOf(
            PostItemUiState.Stamp(emoji = "👍", imageUrl = null, countText = "5"),
            PostItemUiState.Stamp(emoji = "🎉", imageUrl = null, countText = "2"),
            PostItemUiState.Stamp(emoji = ":kotlin:", imageUrl = "https://example.com/kotlin.png", countText = "1"),
        ),
        authorListener = AndroidPreviewAuthorListener,
        menuItems = listOf(PostItemUiState.MenuItem(label = "削除", listener = AndroidPreviewMenuItemListener)),
        postListener = AndroidPreviewPostListener,
        shareListener = AndroidPreviewShareListener,
        listener = AndroidPreviewTimelineItemListener,
    ),
)

private fun previewColumnsPosts(): List<PostItemUiState> = listOf(
    PostItemUiState(
        pinnedLabelVisible = false,
        deletedLabelVisible = false,
        actorLabel = null,
        iconUrl = "https://example.com/kotlin.png",
        displayName = createTextWithEmojis(text = "Kotlin", emojis = listOf()),
        acct = "@kotlin@example.com",
        body = createPostBodyText(content = "Kotlin 2.4 がリリースされました https://kotlinlang.org/", emojis = listOf(), onOpenUrl = {}),
        postedAt = "1分前",
        fullPostedAt = "2026/11/05 12:00",
        linkPreviews = listOf(),
        images = listOf(),
        favouriteCountText = null,
        boostCountText = null,
        favouriteButton = null,
        boostButton = null,
        stamps = listOf(),
        postListener = AndroidPreviewPostListener,
        shareListener = AndroidPreviewShareListener,
        menuItems = listOf(),
        authorListener = AndroidPreviewAuthorListener,
        listener = AndroidPreviewTimelineItemListener,
    ),
)

private fun previewTimelineColumns(items: List<PostItemUiState>, footer: HomeScreenUiState.TimelineFooter): List<HomeScreenUiState.TimelineColumn> = listOf(
    HomeScreenUiState.TimelineColumn(
        title = "メイン",
        listStateKey = "home",
        items = items,
        footer = footer,
        selected = true,
        listener = AndroidPreviewTimelineColumnListener,
    ),
    HomeScreenUiState.TimelineColumn(
        title = "ニュース",
        listStateKey = "news",
        items = items.take(1),
        footer = HomeScreenUiState.TimelineFooter.None,
        selected = false,
        listener = AndroidPreviewTimelineColumnListener,
    ),
)

private object AndroidPreviewTimelineColumnListener : HomeScreenUiState.TimelineColumnListener {
    override fun onClickTab() = Unit

    override fun onClickHeader() = Unit

    override fun onClickEdit() = Unit

    override fun onClickDelete() = Unit

    override fun onScrolledToEnd() = Unit

    override fun onClickRetryLoadMore() = Unit
}

private object AndroidPreviewTimelineColumnEditDialogListener : HomeScreenUiState.TimelineColumnEditDialog.Listener {
    override fun onNameChanged(name: String) = Unit

    override fun onClickAllSources() = Unit

    override fun onClickSave() = Unit

    override fun onDismiss() = Unit
}

private object AndroidPreviewTimelineColumnDeleteDialogListener : HomeScreenUiState.TimelineColumnDeleteDialog.Listener {
    override fun onClickDelete() = Unit

    override fun onDismiss() = Unit
}

private object AndroidPreviewTimelineColumnEditDialogSourceListener : HomeScreenUiState.TimelineColumnEditDialog.SourceListener {
    override fun onClick() = Unit
}
