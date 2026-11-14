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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.AddFollowingGroupDialog
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialog
import net.matsudamper.kmp.activitypub.frontend.ui.EmojiText
import net.matsudamper.kmp.activitypub.frontend.ui.FollowMenu
import net.matsudamper.kmp.activitypub.frontend.ui.PostItem
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemStyle
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentPager
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIcon
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinks
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaGridItem
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
            override fun navigateBack() {
                navigator.back()
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
                IconButton(onClick = uiState.listener::onClickBack) {
                    Icon(imageVector = AppIcons.ArrowBack, contentDescription = "戻る")
                }
                Text(
                    text = "プロフィール",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (uiState.openOriginalMenuItemVisible) {
                    TopBarMenu(
                        blockMenuItemsVisible = uiState.blockMenuItemsVisible,
                        listener = uiState.listener,
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                    LaunchedEffect(selectedGridState, uiState.listener, content.posts.size) {
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
                                            PostItem(uiState = post, style = PostItemStyle.Timeline)
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        }
                                    }
                                }

                                ProfileContentTab.Media -> {
                                    val mediaEmptyMessage = content.mediaEmptyMessage
                                    if (mediaEmptyMessage != null) {
                                        fullLineItem { EmptyMessage(text = mediaEmptyMessage) }
                                    }
                                    items(content.media) { media ->
                                        ProfileMediaGridItem(media = media, modifier = Modifier.padding(1.dp))
                                    }
                                }
                            }
                            postsFooter(footer = content.postsFooter, onClickRetry = uiState.listener::onClickRetryLoadMore)
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
            DropdownMenuItem(
                text = { Text("オリジナルを開く") },
                onClick = {
                    expanded = false
                    listener.onClickOpenOriginal()
                },
            )
            if (blockMenuItemsVisible) {
                DropdownMenuItem(
                    text = { Text("このアカウントをブロック") },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        expanded = false
                        listener.onClickBlockAccount()
                    },
                )
                DropdownMenuItem(
                    text = { Text("このサーバーをブロック") },
                    colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        expanded = false
                        listener.onClickBlockServer()
                    },
                )
            }
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

/**
 * 読み込みの前後で下の要素が動かないよう、比率で高さを決めて先に場所を取る
 */
@Composable
private fun ProfileHeaderImage(headerUrl: String) {
    AsyncImage(
        model = headerUrl,
        contentDescription = "ヘッダー画像",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PROFILE_HEADER_ASPECT_RATIO)
            .background(MaterialTheme.colorScheme.surfaceVariant),
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
            Text(text = profile.biography, style = MaterialTheme.typography.bodyMedium)
        }
        if (profile.links.isNotEmpty()) {
            ProfileLinks(links = profile.links, modifier = Modifier.fillMaxWidth())
        }
    }
}

private const val PROFILE_HEADER_ASPECT_RATIO = 3f

private const val LOAD_MORE_THRESHOLD = 5
