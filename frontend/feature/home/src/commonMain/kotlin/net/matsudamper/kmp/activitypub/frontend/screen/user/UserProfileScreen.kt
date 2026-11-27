package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.home.SensitiveImagesVisibilityButton
import net.matsudamper.kmp.activitypub.frontend.screen.reaction.ReactionPickerDialog
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.AddFollowingGroupDialog
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialog
import net.matsudamper.kmp.activitypub.frontend.ui.EmojiText
import net.matsudamper.kmp.activitypub.frontend.ui.FollowMenu
import net.matsudamper.kmp.activitypub.frontend.ui.PostItem
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemStyle
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentPager
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileHeaderImage
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIcon
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinks
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaGridItem
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaLoadMoreButton
import net.matsudamper.kmp.activitypub.frontend.ui.ReactionConfirmDialog
import net.matsudamper.kmp.activitypub.frontend.ui.URL_COPIED_MESSAGE
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import net.matsudamper.kmp.activitypub.frontend.ui.rememberProfileContentPagerState
import net.matsudamper.kmp.activitypub.frontend.ui.rememberUrlSharer
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun UserProfileScreen(
    username: String,
    navigator: Navigator,
) {
    val viewModel = koinViewModel<UserProfileScreenViewModel>(key = username) { parametersOf(username) }
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val urlSharer = rememberUrlSharer(
        onCopiedToClipboard = { coroutineScope.launch { snackbarHostState.showSnackbar(URL_COPIED_MESSAGE) } },
    )
    LaunchedEffect(viewModel.eventHandler, navigator, uriHandler) {
        viewModel.eventHandler.collect(object : UserProfileScreenViewModel.Event {
            override fun closeToHome() {
                navigator.closeToHome()
            }

            override fun showMessage(message: String) {
                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
            }

            override fun openUrl(url: String) {
                uriHandler.openUri(url)
            }

            override fun shareUrl(url: String) {
                urlSharer(url)
            }

            override fun navigateToImage(urls: List<String>, index: Int) {
                navigator.navigate(Screen.Image(urls = urls, initialIndex = index))
            }

            override fun navigateToPostDetail(postId: String) {
                navigator.navigate(Screen.PostDetail(postId = postId))
            }

            override fun navigateToQuotePost(postId: String) {
                navigator.navigate(Screen.QuotePost(postId = postId))
            }

            override fun navigateToReplyPost(postId: String) {
                navigator.navigate(Screen.ReplyPost(postId = postId))
            }

            override fun navigateToEditPost(noteId: String) {
                navigator.navigate(Screen.EditPost(noteId = noteId))
            }

            override fun navigateToMentionPost(acct: String) {
                navigator.navigate(Screen.MentionPost(acct = acct))
            }

            override fun navigateToUserProfile(username: String) {
                navigator.navigate(Screen.UserProfile(username = username))
            }
        })
    }
    UserProfileContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun UserProfileContent(
    uiState: UserProfileScreenUiState,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(topBarWindowInsets).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = uiState.listener::onClickClose) {
                    Icon(imageVector = AppIcons.Close, contentDescription = "閉じる")
                }
                Text(
                    text = "プロフィール",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (uiState.openOriginalMenuItemVisible) {
                    SensitiveImagesVisibilityButton(
                        sensitiveImagesBlurred = uiState.sensitiveImagesBlurred,
                        onClick = uiState.listener::onClickSensitiveImagesVisibility,
                    )
                    TopBarMenu(
                        blockMenuItemsVisible = uiState.blockMenuItemsVisible,
                        listener = uiState.listener,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState.postFabVisible) {
                FloatingActionButton(
                    onClick = uiState.listener::onClickPostFab,
                    shape = CircleShape,
                ) {
                    Icon(imageVector = AppIcons.Edit, contentDescription = "このユーザー宛てに投稿")
                }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val content = uiState.content) {
                UserProfileScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is UserProfileScreenUiState.Content.Loaded -> {
                    val pagerState = rememberProfileContentPagerState()
                    val selectedGridState = pagerState.gridState(content.selectedTab)
                    // メディアのタブでは、画像の無い続きを読んでも表示の件数が増えない。投稿の件数で見張り直し、末尾にいる間は続きを読み進める
                    LaunchedEffect(selectedGridState, uiState.listener, content.posts.size, content.likes.size) {
                        // 件数も比べ、続きを読んでもまだ末尾が見えているときにもう一度知らせる
                        snapshotFlow {
                            val layoutInfo = selectedGridState.layoutInfo
                            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            val nearEnd = lastVisibleIndex != null && lastVisibleIndex >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
                            nearEnd to layoutInfo.totalItemsCount
                        }
                            .distinctUntilChanged()
                            .filter { (nearEnd, _) -> nearEnd }
                            .collect { uiState.listener.onScrolledToEnd() }
                    }
                    ProfileContentPager(
                        state = pagerState,
                        tabs = content.tabs,
                        selectedTab = content.selectedTab,
                        onSelectTab = uiState.listener::onClickTab,
                        header = {
                            val headerUrl = content.headerUrl
                            if (headerUrl != null) {
                                item {
                                    ProfileHeaderImage(headerUrl = headerUrl)
                                }
                            }
                            item {
                                ProfileHeader(profile = content)
                            }
                        },
                        page = { tab ->
                            when (tab) {
                                ProfileContentTab.Posts -> {
                                    val postsEmptyMessage = content.postsEmptyMessage
                                    if (postsEmptyMessage != null) {
                                        fullLineItem { EmptyMessage(text = postsEmptyMessage) }
                                    }
                                    items(
                                        items = content.posts,
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) { post ->
                                        Column {
                                            PostItem(uiState = post, style = PostItemStyle.Timeline, shareButtonVisible = true)
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                    postsFooter(footer = content.postsFooter, onClickRetry = uiState.listener::onClickRetryLoadMore)
                                }

                                ProfileContentTab.Media -> {
                                    val mediaEmptyMessage = content.mediaEmptyMessage
                                    if (mediaEmptyMessage != null) {
                                        fullLineItem { EmptyMessage(text = mediaEmptyMessage) }
                                    }
                                    items(content.media) { media ->
                                        ProfileMediaGridItem(media = media, modifier = Modifier.padding(1.dp))
                                    }
                                    if (content.mediaLoadMoreButtonVisible) {
                                        fullLineItem { ProfileMediaLoadMoreButton(onClick = uiState.listener::onClickLoadMoreMedia) }
                                    }
                                    postsFooter(footer = content.mediaFooter, onClickRetry = uiState.listener::onClickRetryLoadMore)
                                }

                                ProfileContentTab.Likes -> {
                                    val likesEmptyMessage = content.likesEmptyMessage
                                    if (likesEmptyMessage != null) {
                                        fullLineItem { EmptyMessage(text = likesEmptyMessage) }
                                    }
                                    items(
                                        items = content.likes,
                                        span = { GridItemSpan(maxLineSpan) },
                                    ) { post ->
                                        Column {
                                            PostItem(uiState = post, style = PostItemStyle.Timeline, shareButtonVisible = true)
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                    postsFooter(footer = content.likesFooter, onClickRetry = uiState.listener::onClickRetryLoadMore)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                UserProfileScreenUiState.Content.NotFound -> {
                    Text(text = "ユーザーが見つかりません", modifier = Modifier.align(Alignment.Center))
                }

                is UserProfileScreenUiState.Content.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(text = content.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = uiState.listener::onClickRetry) { Text("再読み込み") }
                    }
                }
            }
        }
    }
    val deleteNoteDialog = uiState.deleteNoteDialog
    if (deleteNoteDialog != null) {
        DeleteNoteDialog(uiState = deleteNoteDialog)
    }
    val reactionPickerDialog = uiState.reactionPickerDialog
    if (reactionPickerDialog != null) {
        ReactionPickerDialog(dialogUiState = reactionPickerDialog)
    }
    val reactionConfirmDialog = uiState.reactionConfirmDialog
    if (reactionConfirmDialog != null) {
        ReactionConfirmDialog(uiState = reactionConfirmDialog)
    }
    val addFollowingGroupDialog = uiState.addFollowingGroupDialog
    if (addFollowingGroupDialog != null) {
        AddFollowingGroupDialog(uiState = addFollowingGroupDialog)
    }
    val blockDialog = uiState.blockDialog
    if (blockDialog != null) {
        BlockDialog(uiState = blockDialog)
    }
}

@Composable
private fun BlockDialog(uiState: UserProfileScreenUiState.BlockDialog) {
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text(uiState.title) },
        text = { Text(uiState.message) },
        confirmButton = {
            TextButton(
                onClick = uiState.listener::onClickBlock,
                enabled = uiState.buttonsEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("ブロック")
            }
        },
        dismissButton = {
            TextButton(
                onClick = uiState.listener::onDismiss,
                enabled = uiState.buttonsEnabled,
            ) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
private fun TopBarMenu(
    blockMenuItemsVisible: Boolean,
    listener: UserProfileScreenUiState.Listener,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(imageVector = AppIcons.MoreVert, contentDescription = "メニュー")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            TopBarMenuItems(
                blockMenuItemsVisible = blockMenuItemsVisible,
                listener = listener,
                onItemClick = { expanded = false },
            )
        }
    }
}

/**
 * @param onItemClick どの項目を押しても、メニューを閉じるために先に呼ぶ
 */
@Composable
internal fun TopBarMenuItems(
    blockMenuItemsVisible: Boolean,
    listener: UserProfileScreenUiState.Listener,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        DropdownMenuItem(
            text = { Text("オリジナルを開く") },
            onClick = {
                onItemClick()
                listener.onClickOpenOriginal()
            },
        )
        if (blockMenuItemsVisible) {
            DropdownMenuItem(
                text = { Text("アカウントをブロック") },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                onClick = {
                    onItemClick()
                    listener.onClickBlockAccount()
                },
            )
            DropdownMenuItem(
                text = { Text("サーバーをブロック") },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                onClick = {
                    onItemClick()
                    listener.onClickBlockServer()
                },
            )
        }
    }
}

private fun LazyGridScope.fullLineItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.postsFooter(
    footer: UserProfileScreenUiState.PostsFooter,
    onClickRetry: () -> Unit,
) {
    when (footer) {
        UserProfileScreenUiState.PostsFooter.None -> Unit

        UserProfileScreenUiState.PostsFooter.Loading -> fullLineItem {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UserProfileScreenUiState.PostsFooter.Error -> fullLineItem {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = footer.message, color = MaterialTheme.colorScheme.error)
                Button(onClick = onClickRetry) { Text("再読み込み") }
            }
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ProfileHeader(
    profile: UserProfileScreenUiState.Content.Loaded,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProfileIcon(iconUrl = profile.iconUrl, contentDescription = "プロフィール画像", modifier = Modifier.size(72.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                EmojiText(
                    text = profile.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Unspecified,
                    fontWeight = FontWeight.Bold,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    text = profile.acct,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val followMenu = profile.followMenu
        if (followMenu != null) {
            FollowMenu(uiState = followMenu)
        }
        if (profile.biography != null) {
            EmojiText(
                text = profile.biography,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Unspecified,
                fontWeight = null,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
        }
        if (profile.links.isNotEmpty()) {
            ProfileLinks(links = profile.links, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FollowCount(count = profile.followingCount, label = "購読")
            FollowCount(count = profile.followersCount, label = "フォロワー")
        }
    }
}

@Composable
private fun FollowCount(
    count: String,
    label: String,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = count,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 5
