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
import androidx.compose.ui.unit.Dp
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
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppFontSize
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile
import net.matsudamper.kmp.activitypub.frontend.ui.PostItem
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemStyle
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinkUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaUiState
import net.matsudamper.kmp.activitypub.frontend.ui.previewBoostButton
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
                PostItem(uiState = previewTimelinePosts().first(), style = PostItemStyle.Timeline, shareButtonVisible = true)
                Text(text = "先頭から離れている", modifier = Modifier.padding(16.dp))
                TimelineHeaderDivider(newPostsHiddenAbove = true)
                PostItem(uiState = previewTimelinePosts().first(), style = PostItemStyle.Timeline, shareButtonVisible = true)
            }
        }
    }
}

@Preview
@Composable
private fun TimelineContentWarningItemsPreview() {
    HomePreviewTheme {
        Surface {
            Column {
                val post = previewTimelinePosts().first()
                PostItem(
                    uiState = post.copy(
                        contentWarning = PostItemUiState.ContentWarning(
                            text = createTextWithEmojis(text = "映画のネタバレ :kotlin:", emojis = PreviewEmojis),
                            toggleButtonText = "もっと見る",
                            listener = AndroidPreviewContentWarningListener,
                        ),
                        bodyVisible = false,
                        translateButton = null,
                    ),
                    style = PostItemStyle.Timeline,
                    shareButtonVisible = true,
                )
                PostItem(
                    uiState = post.copy(
                        contentWarning = PostItemUiState.ContentWarning(
                            text = createTextWithEmojis(text = "映画のネタバレ :kotlin:", emojis = PreviewEmojis),
                            toggleButtonText = "隠す",
                            listener = AndroidPreviewContentWarningListener,
                        ),
                        bodyVisible = true,
                    ),
                    style = PostItemStyle.Timeline,
                    shareButtonVisible = true,
                )
            }
        }
    }
}

@Preview
@Composable
private fun TimelineReplyItemsPreview() {
    HomePreviewTheme {
        Surface {
            Column {
                val post = previewTimelinePosts().first()
                val replyLabel = PostItemUiState.ReplyLabel(
                    target = PostItemUiState.ReplyLabelTarget(
                        iconUrl = null,
                        name = createTextWithEmojis(text = "Alice :kotlin:", emojis = PreviewEmojis),
                        listener = AndroidPreviewReplyLabelListener,
                    ),
                )
                listOf(
                    post.copy(replyLabel = replyLabel),
                    post.copy(
                        replyLabel = replyLabel,
                        actorLabel = PostItemUiState.ActorLabel(
                            icon = PostItemUiState.ActorLabelIcon.Boost,
                            actorName = createTextWithEmojis(text = "Bob", emojis = listOf()),
                            actionText = "がブースト",
                            iconUrl = null,
                            cancelledLabelVisible = false,
                            listener = AndroidPreviewActorLabelListener,
                        ),
                    ),
                    post.copy(replyLabel = PostItemUiState.ReplyLabel(target = null)),
                ).forEach { item ->
                    PostItem(uiState = item, style = PostItemStyle.Timeline, shareButtonVisible = true)
                    HorizontalDivider()
                }
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
                            actorName = createTextWithEmojis(text = "Alice :kotlin:", emojis = PreviewEmojis),
                            actionText = "がお気に入り",
                            iconUrl = null,
                            cancelledLabelVisible = false,
                            listener = AndroidPreviewActorLabelListener,
                        ),
                    ),
                    notifiedPost.copy(
                        actorLabel = PostItemUiState.ActorLabel(
                            icon = PostItemUiState.ActorLabelIcon.Boost,
                            actorName = createTextWithEmojis(text = "Bob", emojis = listOf()),
                            actionText = "がブースト",
                            iconUrl = null,
                            cancelledLabelVisible = false,
                            listener = AndroidPreviewActorLabelListener,
                        ),
                    ),
                    notifiedPost.copy(deletedLabelVisible = true),
                    posts[1].copy(deletedLabelVisible = true),
                ).forEach { item ->
                    PostItem(uiState = item, style = PostItemStyle.Timeline, shareButtonVisible = true)
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
                notifications = HomeScreenUiState.Notifications.Loading,
                search = HomeScreenUiState.Search(
                    query = "",
                    queryLabel = "ユーザーを検索",
                    targets = previewSearchTargets(selectedLabel = "ユーザー"),
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userContentTab = ProfileContentTab.Posts,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                openedColumn = null,
                openedColumnWidth = 400.dp,
                railItems = listOf(),
                deleteNoteDialog = deleteNoteDialog,
                reactionPickerDialog = null,
                reactionConfirmDialog = null,
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
                notifications = HomeScreenUiState.Notifications.Loading,
                search = HomeScreenUiState.Search(
                    query = "kotlin",
                    queryLabel = "ユーザーを検索",
                    targets = previewSearchTargets(selectedLabel = "ユーザー"),
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
                openedColumnWidth = 400.dp,
                railItems = listOf(),
                deleteNoteDialog = null,
                reactionPickerDialog = null,
                reactionConfirmDialog = null,
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
private fun HomeNotificationsContentPreview() {
    HomePreviewTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Notifications,
                timelineColumns = listOf(),
                timelineColumnsEmptyMessage = null,
                addTimelineColumnButtonEnabled = true,
                timelineColumnEditDialog = null,
                timelineColumnDeleteDialog = null,
                timelineRefreshing = false,
                timelineStreamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
                sensitiveImagesBlurred = true,
                notifications = HomeScreenUiState.Notifications.Loaded(
                    items = previewTimelinePosts(),
                    emptyMessage = null,
                    refreshing = false,
                    footer = HomeScreenUiState.TimelineFooter.None,
                ),
                search = HomeScreenUiState.Search(
                    query = "",
                    queryLabel = "ユーザーを検索",
                    targets = previewSearchTargets(selectedLabel = "ユーザー"),
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userContentTab = ProfileContentTab.Posts,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                openedColumn = null,
                openedColumnWidth = 400.dp,
                railItems = listOf(),
                deleteNoteDialog = null,
                reactionPickerDialog = null,
                reactionConfirmDialog = null,
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
                notifications = HomeScreenUiState.Notifications.Loading,
                search = HomeScreenUiState.Search(
                    query = "unknown",
                    queryLabel = "ユーザーを検索",
                    targets = previewSearchTargets(selectedLabel = "ユーザー"),
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
                openedColumnWidth = 400.dp,
                railItems = listOf(),
                deleteNoteDialog = null,
                reactionPickerDialog = null,
                reactionConfirmDialog = null,
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
private fun HomeSearchPostsContentPreview() {
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
                notifications = HomeScreenUiState.Notifications.Loading,
                search = HomeScreenUiState.Search(
                    query = "kotlin",
                    queryLabel = "投稿を検索",
                    targets = previewSearchTargets(selectedLabel = "投稿"),
                    result = HomeScreenUiState.SearchResult.PostsLoaded(
                        items = previewTimelinePosts(),
                        emptyMessage = null,
                        loadMoreButtonVisible = true,
                    ),
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userContentTab = ProfileContentTab.Posts,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                openedColumn = null,
                openedColumnWidth = 400.dp,
                railItems = listOf(),
                deleteNoteDialog = null,
                reactionPickerDialog = null,
                reactionConfirmDialog = null,
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
                notifications = HomeScreenUiState.Notifications.Loading,
                search = HomeScreenUiState.Search(
                    query = "",
                    queryLabel = "ユーザーを検索",
                    targets = previewSearchTargets(selectedLabel = "ユーザー"),
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loaded(
                    headerUrl = "https://example.com/header.png",
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
                            replyLabel = null,
                            pinnedLabelVisible = false,
                            deletedLabelVisible = false,
                            actorLabel = null,
                            iconUrl = "https://example.com/icon.png",
                            displayName = createTextWithEmojis(text = "ユーザー1", emojis = listOf()),
                            acct = "@user1",
                            contentWarning = null,
                            bodyVisible = true,
                            body = createPostBodyText(content = "はじめての投稿", mentions = listOf(), emojis = listOf(), onOpenUrl = {}, onOpenProfile = {}),
                            postedAt = "3分前",
                            fullPostedAt = "2026/11/05 12:00",
                            editedLabel = null,
                            linkPreviews = listOf(),
                            images = listOf(),
                            videos = listOf(),
                            replyCountText = null,
                            replyListener = null,
                            quote = null,
                            favouriteCountText = null,
                            boostCountText = null,
                            favouriteButton = null,
                            boostButton = null,
                            reactionButton = null,
                            stamps = listOf(),
                            authorListener = null,
                            translateButton = null,
                            menuItems = listOf(PostItemUiState.MenuItem(label = "共有", listener = AndroidPreviewMenuItemListener)),
                            postListener = null,
                            shareListener = AndroidPreviewShareListener,
                            listener = AndroidPreviewTimelineItemListener,
                        ),
                    ),
                    emptyMessage = null,
                    postsFooter = HomeScreenUiState.TimelineFooter.None,
                    media = listOf(),
                    mediaEmptyMessage = "表示できるメディアはありません",
                    likes = listOf(),
                    likesEmptyMessage = "表示できるいいねはありません",
                    likesFooter = HomeScreenUiState.TimelineFooter.None,
                ),
                userTabIconUrl = "https://example.com/icon.png",
                openedColumn = null,
                openedColumnWidth = 400.dp,
                railItems = listOf(),
                deleteNoteDialog = null,
                reactionPickerDialog = null,
                reactionConfirmDialog = null,
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
                notifications = HomeScreenUiState.Notifications.Loading,
                search = HomeScreenUiState.Search(
                    query = "",
                    queryLabel = "ユーザーを検索",
                    targets = previewSearchTargets(selectedLabel = "ユーザー"),
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loaded(
                    headerUrl = null,
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
                    postsFooter = HomeScreenUiState.TimelineFooter.None,
                    media = listOf(),
                    mediaEmptyMessage = "表示できるメディアはありません",
                    likes = listOf(),
                    likesEmptyMessage = "表示できるいいねはありません",
                    likesFooter = HomeScreenUiState.TimelineFooter.None,
                ),
                userTabIconUrl = "https://example.com/icon.png",
                openedColumn = null,
                openedColumnWidth = 400.dp,
                railItems = listOf(),
                deleteNoteDialog = null,
                reactionPickerDialog = null,
                reactionConfirmDialog = null,
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
                notifications = HomeScreenUiState.Notifications.Loading,
                search = HomeScreenUiState.Search(
                    query = "",
                    queryLabel = "ユーザーを検索",
                    targets = previewSearchTargets(selectedLabel = "ユーザー"),
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loaded(
                    headerUrl = null,
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
                            replyLabel = null,
                            pinnedLabelVisible = false,
                            deletedLabelVisible = false,
                            actorLabel = null,
                            iconUrl = "https://example.com/icon.png",
                            displayName = createTextWithEmojis(text = "ユーザー1", emojis = listOf()),
                            acct = "@user1",
                            contentWarning = null,
                            bodyVisible = true,
                            body = createPostBodyText(content = "はじめての投稿", mentions = listOf(), emojis = listOf(), onOpenUrl = {}, onOpenProfile = {}),
                            postedAt = "3分前",
                            fullPostedAt = "2026/11/05 12:00",
                            editedLabel = null,
                            linkPreviews = listOf(),
                            images = listOf(),
                            videos = listOf(),
                            replyCountText = null,
                            replyListener = null,
                            quote = null,
                            favouriteCountText = null,
                            boostCountText = null,
                            favouriteButton = null,
                            boostButton = null,
                            reactionButton = null,
                            stamps = listOf(),
                            authorListener = null,
                            translateButton = null,
                            menuItems = listOf(PostItemUiState.MenuItem(label = "共有", listener = AndroidPreviewMenuItemListener)),
                            postListener = null,
                            shareListener = AndroidPreviewShareListener,
                            listener = AndroidPreviewTimelineItemListener,
                        ),
                    ),
                    emptyMessage = null,
                    postsFooter = HomeScreenUiState.TimelineFooter.None,
                    media = List(5) { index ->
                        ProfileMediaUiState(
                            url = "https://example.com/media/$index.png",
                            description = null,
                            blurred = index == 2,
                            listener = AndroidPreviewProfileMediaListener,
                        )
                    },
                    mediaEmptyMessage = null,
                    likes = listOf(),
                    likesEmptyMessage = "表示できるいいねはありません",
                    likesFooter = HomeScreenUiState.TimelineFooter.None,
                ),
                userTabIconUrl = "https://example.com/icon.png",
                openedColumn = null,
                openedColumnWidth = 400.dp,
                railItems = listOf(),
                deleteNoteDialog = null,
                reactionPickerDialog = null,
                reactionConfirmDialog = null,
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
private fun HomeColumnsContentPreview() = HomeColumnsPreview(
    openedColumn = HomeScreenUiState.Column.Post,
    timelineColumnsEmptyMessage = null,
    timelineColumns = previewTimelineColumns(previewColumnsPosts(), HomeScreenUiState.TimelineFooter.None),
)

/**
 * 文字の大きさの小と大、カラムの幅の通常と一番細い幅（HomeScreenViewModel の COLUMN_MIN_WIDTH）を組み合わせて並べる
 */
@Preview(widthDp = 1440, heightDp = 800)
@Composable
private fun HomeColumnsContentPreviewTimelineColumns() = HomeColumnsPreview(
    openedColumn = null,
    timelineColumnsEmptyMessage = null,
    timelineColumns = listOf(
        AppFontSize.Small to 400.dp,
        AppFontSize.Small to 240.dp,
        AppFontSize.Large to 400.dp,
        AppFontSize.Large to 240.dp,
    ).map { (fontSize, width) ->
        val fontSizeLabel = if (fontSize == AppFontSize.Small) "小" else "大"
        HomeScreenUiState.TimelineColumn(
            title = "$fontSizeLabel ${width.value.toInt()}dp",
            listStateKey = "${fontSize.name}-$width",
            items = previewTimelinePosts().take(1),
            footer = HomeScreenUiState.TimelineFooter.None,
            selected = false,
            fontSize = fontSize,
            fontSizeMenuItems = previewFontSizeMenuItems(checkedLabel = fontSizeLabel),
            width = width,
            listener = AndroidPreviewTimelineColumnListener,
        )
    },
)

@Preview(widthDp = 1280, heightDp = 800)
@Composable
private fun HomeColumnsContentPreviewTimelineColumnsEmpty() = HomeColumnsPreview(
    openedColumn = null,
    timelineColumnsEmptyMessage = "表示するカラムがありません",
    timelineColumns = listOf(),
)

/**
 * @param timelineColumnsEmptyMessage カラムが無いときの案内。カラムを並べるときは null
 */
@Composable
private fun HomeColumnsPreview(
    openedColumn: HomeScreenUiState.Column?,
    timelineColumnsEmptyMessage: String?,
    timelineColumns: List<HomeScreenUiState.TimelineColumn>,
) {
    HomePreviewTheme {
        HomeColumnsContent(
            uiState = HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Home,
                timelineColumns = timelineColumns,
                timelineColumnsEmptyMessage = timelineColumnsEmptyMessage,
                addTimelineColumnButtonEnabled = true,
                timelineColumnEditDialog = null,
                timelineColumnDeleteDialog = null,
                timelineRefreshing = false,
                timelineStreamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
                sensitiveImagesBlurred = true,
                notifications = HomeScreenUiState.Notifications.Loading,
                search = HomeScreenUiState.Search(
                    query = "kotlin",
                    queryLabel = "ユーザーを検索",
                    targets = previewSearchTargets(selectedLabel = "ユーザー"),
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
                reactionPickerDialog = null,
                reactionConfirmDialog = null,
                addFollowingGroupDialog = null,
                openedColumn = openedColumn,
                openedColumnWidth = 400.dp,
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
                        navigationIcon = PostNavigationIcon.Close,
                        uiState = PostScreenUiState(
                            title = "投稿",
                            body = "",
                            bodyInsertion = null,
                            contentWarningInputVisible = false,
                            contentWarning = "",
                            contentWarningButtonVisible = true,
                            contentWarningButtonChecked = false,
                            contentWarningButtonEnabled = true,
                            bodyLoadingVisible = false,
                            inputHasError = false,
                            inputEnabled = true,
                            closeButtonEnabled = true,
                            postButtonEnabled = false,
                            postButtonText = "投稿",
                            images = listOf(),
                            quote = null,
                            replyTarget = null,
                            editTarget = null,
                            discardDraftDialog = null,
                            addImageButtonEnabled = true,
                            addImageLoadingVisible = false,
                            addEmojiButtonEnabled = true,
                            sensitiveChipSelected = false,
                            sensitiveChipEnabled = true,
                            emojiPickerDialog = null,
                            listener = AndroidPreviewPostScreenListener,
                        ),
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

    override fun onResizeOpenedColumn(delta: Dp) = Unit

    override fun onResizeOpenedColumnEnd() = Unit

    override fun onPostColumnFinished() = Unit

    override fun onPostColumnTargetChanged() = Unit

    override fun onTabChanged(tab: HomeScreenUiState.Tab) = Unit

    override fun onRefreshTimeline() = Unit

    override fun onClickAddTimelineColumn() = Unit

    override fun onMoveTimelineColumn(fromIndex: Int, toIndex: Int) = Unit

    override fun onClickTimelineStreamStatus() = Unit

    override fun onClickSensitiveImagesVisibility() = Unit

    override fun onRefreshNotifications() = Unit

    override fun onClickRetryNotifications() = Unit

    override fun onScrolledToNotificationsEnd() = Unit

    override fun onClickRetryLoadMoreNotifications() = Unit

    override fun onSearchQueryChanged(query: String) = Unit

    override fun onSearch() = Unit

    override fun onClickLoadMoreSearchResult() = Unit

    override fun onClickEditProfile() = Unit

    override fun onClickRetryProfile() = Unit

    override fun onClickRetryUserPosts() = Unit

    override fun onClickUserContentTab(tab: ProfileContentTab) = Unit

    override fun onScrolledToUserContentEnd() = Unit

    override fun onClickRetryLoadMoreUserContent() = Unit

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

private object AndroidPreviewTranslateButtonListener : PostItemUiState.TranslateButtonListener {
    override fun onClick() = Unit
}

private object AndroidPreviewReplyLabelListener : PostItemUiState.ReplyLabelListener {
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

private object AndroidPreviewSearchTargetListener : HomeScreenUiState.SearchTargetListener {
    override fun onClick() = Unit
}

private fun previewSearchTargets(selectedLabel: String): List<HomeScreenUiState.SearchTarget> =
    listOf("ユーザー", "投稿").map { label ->
        HomeScreenUiState.SearchTarget(label = label, selected = label == selectedLabel, listener = AndroidPreviewSearchTargetListener)
    }

private object AndroidPreviewProfileLinkListener : ProfileLinkUiState.Listener {
    override fun onClick() = Unit
}

private object AndroidPreviewPostScreenListener : PostScreenUiState.Listener {
    override fun onBodyChanged(text: String) = Unit

    override fun onBodyInsertionHandled() = Unit

    override fun onClickContentWarning() = Unit

    override fun onContentWarningChanged(text: String) = Unit

    override fun onClickAddImage() = Unit

    override fun onImagePicked(file: PickedImageFile) = Unit

    override fun onClickAddEmoji() = Unit

    override fun onClickSensitive() = Unit

    override fun onReplyRequested(postId: String) = Unit

    override fun onEditRequested(noteId: String) = Unit

    override fun onClickPost() = Unit

    override fun onPressPostShortcut() = Unit

    override fun onClickClose() = Unit
}

private fun previewTimelinePosts(): List<PostItemUiState> = listOf(
    PostItemUiState(
        replyLabel = null,
        pinnedLabelVisible = false,
        deletedLabelVisible = false,
        actorLabel = null,
        iconUrl = "https://example.com/kotlin.png",
        displayName = createTextWithEmojis(text = "Kotlin", emojis = listOf()),
        acct = "@kotlin@example.com",
        contentWarning = null,
        bodyVisible = true,
        body = createPostBodyText(content = "Kotlin 2.4 がリリースされました https://kotlinlang.org/", mentions = listOf(), emojis = listOf(), onOpenUrl = {}, onOpenProfile = {}),
        postedAt = "1分前",
        fullPostedAt = "2026/11/05 12:00",
        editedLabel = null,
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
        videos = listOf(
            PostItemUiState.Video(url = "https://example.com/movie.mp4", description = "動画の説明", firstFrameVisible = true),
        ),
        replyCountText = "123",
        replyListener = null,
        quote = null,
        favouriteCountText = "12345",
        boostCountText = "1234",
        favouriteButton = null,
        boostButton = previewBoostButton(boosted = true),
        reactionButton = null,
        stamps = listOf(),
        authorListener = AndroidPreviewAuthorListener,
        translateButton = PostItemUiState.TranslateButton(text = "翻訳", enabled = true, listener = AndroidPreviewTranslateButtonListener),
        menuItems = listOf(PostItemUiState.MenuItem(label = "共有", listener = AndroidPreviewMenuItemListener)),
        postListener = AndroidPreviewPostListener,
        shareListener = AndroidPreviewShareListener,
        listener = AndroidPreviewTimelineItemListener,
    ),
    PostItemUiState(
        replyLabel = null,
        pinnedLabelVisible = false,
        deletedLabelVisible = false,
        actorLabel = PostItemUiState.ActorLabel(
            icon = PostItemUiState.ActorLabelIcon.Boost,
            actorName = createTextWithEmojis(text = "Alice :kotlin: とても長い名前のユーザーとても長い名前のユーザー", emojis = PreviewEmojis),
            actionText = "がブースト",
            iconUrl = null,
            cancelledLabelVisible = true,
            listener = AndroidPreviewActorLabelListener,
        ),
        iconUrl = null,
        displayName = createTextWithEmojis(text = "Compose :compose:", emojis = PreviewEmojis),
        acct = "@compose@example.com",
        contentWarning = null,
        bodyVisible = true,
        body = createPostBodyText(content = "Compose Multiplatform :compose: で Web と Android の画面を共有している :kotlin:", mentions = listOf(), emojis = PreviewEmojis, onOpenUrl = {}, onOpenProfile = {}),
        postedAt = "5分前",
        fullPostedAt = "2026/11/05 12:00",
        editedLabel = null,
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
        videos = listOf(),
        replyCountText = null,
        replyListener = null,
        quote = null,
        favouriteCountText = "3",
        boostCountText = null,
        favouriteButton = PostItemUiState.FavouriteButton(
            iconHighlighted = true,
            listener = AndroidPreviewFavouriteButtonListener,
        ),
        boostButton = previewBoostButton(boosted = false),
        reactionButton = null,
        stamps = listOf(
            PostItemUiState.Stamp(emoji = "👍", imageUrl = null, countText = "5", selected = false, listener = null),
            PostItemUiState.Stamp(emoji = "🎉", imageUrl = null, countText = "2", selected = false, listener = null),
            PostItemUiState.Stamp(emoji = ":kotlin:", imageUrl = "https://example.com/kotlin.png", countText = "1", selected = false, listener = null),
        ),
        authorListener = AndroidPreviewAuthorListener,
        translateButton = null,
        menuItems = listOf(PostItemUiState.MenuItem(label = "共有", listener = AndroidPreviewMenuItemListener), PostItemUiState.MenuItem(label = "削除", listener = AndroidPreviewMenuItemListener)),
        postListener = AndroidPreviewPostListener,
        shareListener = AndroidPreviewShareListener,
        listener = AndroidPreviewTimelineItemListener,
    ),
)

private fun previewColumnsPosts(): List<PostItemUiState> = listOf(
    PostItemUiState(
        replyLabel = null,
        pinnedLabelVisible = false,
        deletedLabelVisible = false,
        actorLabel = null,
        iconUrl = "https://example.com/kotlin.png",
        displayName = createTextWithEmojis(text = "Kotlin", emojis = listOf()),
        acct = "@kotlin@example.com",
        contentWarning = null,
        bodyVisible = true,
        body = createPostBodyText(content = "Kotlin 2.4 がリリースされました https://kotlinlang.org/", mentions = listOf(), emojis = listOf(), onOpenUrl = {}, onOpenProfile = {}),
        postedAt = "1分前",
        fullPostedAt = "2026/11/05 12:00",
        editedLabel = null,
        linkPreviews = listOf(),
        images = listOf(),
        videos = listOf(),
        replyCountText = null,
        replyListener = null,
        quote = null,
        favouriteCountText = null,
        boostCountText = null,
        favouriteButton = null,
        boostButton = null,
        reactionButton = null,
        stamps = listOf(),
        postListener = AndroidPreviewPostListener,
        shareListener = AndroidPreviewShareListener,
        menuItems = listOf(PostItemUiState.MenuItem(label = "共有", listener = AndroidPreviewMenuItemListener)),
        authorListener = AndroidPreviewAuthorListener,
        translateButton = null,
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
        fontSize = AppFontSize.Medium,
        fontSizeMenuItems = previewFontSizeMenuItems(checkedLabel = "中"),
        width = 400.dp,
        listener = AndroidPreviewTimelineColumnListener,
    ),
    HomeScreenUiState.TimelineColumn(
        title = "ニュース",
        listStateKey = "news",
        items = items.take(1),
        footer = HomeScreenUiState.TimelineFooter.None,
        selected = false,
        fontSize = AppFontSize.Large,
        fontSizeMenuItems = previewFontSizeMenuItems(checkedLabel = "大"),
        width = 400.dp,
        listener = AndroidPreviewTimelineColumnListener,
    ),
)

private fun previewFontSizeMenuItems(checkedLabel: String): List<HomeScreenUiState.FontSizeMenuItem> =
    listOf("小", "中", "大").map { label ->
        HomeScreenUiState.FontSizeMenuItem(label = label, checked = label == checkedLabel, listener = AndroidPreviewFontSizeMenuItemListener)
    }

private object AndroidPreviewTimelineColumnListener : HomeScreenUiState.TimelineColumnListener {
    override fun onClickTab() = Unit

    override fun onClickHeader() = Unit

    override fun onClickEdit() = Unit

    override fun onClickDelete() = Unit

    override fun onScrolledToEnd() = Unit

    override fun onClickRetryLoadMore() = Unit

    override fun onResize(delta: Dp) = Unit

    override fun onResizeEnd() = Unit
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

private object AndroidPreviewFontSizeMenuItemListener : HomeScreenUiState.FontSizeMenuItemListener {
    override fun onClick() = Unit
}

private object AndroidPreviewContentWarningListener : PostItemUiState.ContentWarningListener {
    override fun onClickToggle() = Unit
}
