package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostContentWithImageInput
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostNavigationIcon
import net.matsudamper.kmp.activitypub.frontend.screen.post.rememberPostScreenUiState
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.AddFollowingGroupDialog
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialog
import net.matsudamper.kmp.activitypub.frontend.ui.EmojiText
import net.matsudamper.kmp.activitypub.frontend.ui.FollowMenu
import net.matsudamper.kmp.activitypub.frontend.ui.PostItem
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTabRow
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileGridCells
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIcon
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinks
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaGridItem
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextFieldKind
import net.matsudamper.kmp.activitypub.frontend.ui.URL_COPIED_MESSAGE
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import net.matsudamper.kmp.activitypub.frontend.ui.rememberUrlSharer
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun HomeScreen(
    navigator: Navigator,
    tab: Screen.Home.Tab,
) {
    val viewModel = koinViewModel<HomeScreenViewModel> { parametersOf(tab.toUiStateTab()) }
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val urlSharer = rememberUrlSharer(
        onCopiedToClipboard = { coroutineScope.launch { snackbarHostState.showSnackbar(URL_COPIED_MESSAGE) } },
    )
    val tabListStates = rememberHomeTabListStates()

    LaunchedEffect(viewModel.eventHandler, navigator, uriHandler, tabListStates) {
        viewModel.eventHandler.collect(
            object : HomeScreenViewModel.Event {
                override fun navigateToTab(tab: HomeScreenUiState.Tab) {
                    navigator.selectHomeTab(tab.toScreenTab())
                }

                override fun scrollTabToTop(tab: HomeScreenUiState.Tab) {
                    coroutineScope.launch { tabListStates.scrollToTop(tab) }
                }

                override fun scrollTimelineColumnToTop(listStateKey: String) {
                    coroutineScope.launch { tabListStates.timeline(listStateKey).animateScrollToItem(0) }
                }

                override fun navigateToPost() {
                    navigator.navigate(Screen.Post)
                }

                override fun navigateToSettings() {
                    navigator.navigate(Screen.Settings)
                }

                override fun navigateToUserProfile(username: String) {
                    navigator.navigate(Screen.UserProfile(username = username))
                }

                override fun navigateToFollowing() {
                    navigator.navigate(Screen.Following)
                }

                override fun navigateToFollowers() {
                    navigator.navigate(Screen.Followers)
                }

                override fun navigateToProfileEdit() {
                    navigator.navigate(Screen.ProfileEdit)
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
            },
        )
    }

    LaunchedEffect(tab, uiState.listener) {
        uiState.listener.onTabChanged(tab.toUiStateTab())
    }

    LaunchedEffect(uiState.listener) {
        uiState.listener.onScreenShown()
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        if (maxWidth >= COLUMNS_LAYOUT_MIN_WIDTH) {
            // 投稿の知らせはホームの下に出す。投稿カラムは閉じていることがある
            val postUiState = rememberPostScreenUiState(
                onNavigateBack = uiState.listener::onPostColumnFinished,
                snackbarHostState = snackbarHostState,
            )
            val postColumnSnackbarHostState = remember { SnackbarHostState() }
            HomeColumnsContent(
                uiState = uiState,
                snackbarHostState = snackbarHostState,
                tabListStates = tabListStates,
                postColumn = { modifier ->
                    Box(modifier = modifier) {
                        PostContentWithImageInput(
                            uiState = postUiState,
                            navigationIcon = PostNavigationIcon.Close,
                            snackbarHostState = postColumnSnackbarHostState,
                        )
                    }
                },
            )
        } else {
            HomeContent(uiState = uiState, snackbarHostState = snackbarHostState, tabListStates = tabListStates)
        }
    }
}

/**
 * タブを押し直したときに先頭へ戻すため、タブの中身ではなく画面で持つ
 */
@Stable
internal class HomeTabListStates(
    val search: LazyListState,
    val user: LazyGridState,
    private val timelineListStates: TimelineListStates,
) {
    fun timeline(listStateKey: String): LazyListState = timelineListStates.get(listStateKey)

    suspend fun scrollToTop(tab: HomeScreenUiState.Tab) {
        when (tab) {
            HomeScreenUiState.Tab.Home -> timelineListStates.all().forEach { it.animateScrollToItem(0) }
            HomeScreenUiState.Tab.Search -> search.animateScrollToItem(0)
            HomeScreenUiState.Tab.User -> user.animateScrollToItem(0)
        }
    }
}

@Composable
internal fun rememberHomeTabListStates(): HomeTabListStates {
    val search = rememberLazyListState()
    val user = rememberLazyGridState()
    val timeline = rememberSaveable(saver = TimelineListStates.Saver) { TimelineListStates(initialPositions = mapOf()) }
    return remember(search, user, timeline) { HomeTabListStates(search = search, user = user, timelineListStates = timeline) }
}

/**
 * カラムは足し引きされるので、カラムごとに初めて出したときに作る。
 * 投稿詳細などから戻ったときに位置を戻せるよう、カラムごとの位置を保存する
 */
@Stable
internal class TimelineListStates(initialPositions: Map<String, TimelineListPosition>) {
    private val states = initialPositions.mapValuesTo(mutableMapOf()) { (_, position) ->
        LazyListState(firstVisibleItemIndex = position.index, firstVisibleItemScrollOffset = position.offset)
    }

    fun get(listStateKey: String): LazyListState = states.getOrPut(listStateKey) { LazyListState() }

    fun all(): List<LazyListState> = states.values.toList()

    companion object {
        val Saver: Saver<TimelineListStates, Any> = listSaver(
            save = { timelineListStates ->
                timelineListStates.states.flatMap { (listStateKey, state) ->
                    listOf(listStateKey, state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
                }
            },
            restore = { saved ->
                TimelineListStates(
                    initialPositions = saved.chunked(3).associate { (listStateKey, index, offset) ->
                        listStateKey as String to TimelineListPosition(index = index as Int, offset = offset as Int)
                    },
                )
            },
        )
    }
}

internal data class TimelineListPosition(
    val index: Int,
    val offset: Int,
)

@Composable
internal fun HomeContent(
    uiState: HomeScreenUiState,
    snackbarHostState: SnackbarHostState,
    tabListStates: HomeTabListStates,
) {
    val tabStateHolder = rememberSaveableStateHolder()
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(topBarWindowInsets).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uiState.selectedTab.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    if (uiState.selectedTab == HomeScreenUiState.Tab.Home) {
                        val selectedColumn = uiState.timelineColumns.firstOrNull { it.selected }
                        if (selectedColumn != null) {
                            IconButton(onClick = selectedColumn.listener::onClickRemove) {
                                Icon(
                                    imageVector = AppIcons.Close,
                                    contentDescription = "${selectedColumn.title}のタブを閉じる",
                                )
                            }
                        }
                        SensitiveImagesVisibilityButton(
                            sensitiveImagesBlurred = uiState.sensitiveImagesBlurred,
                            onClick = uiState.listener::onClickSensitiveImagesVisibility,
                        )
                        TimelineStreamStatusButton(
                            status = uiState.timelineStreamStatus,
                            onClick = uiState.listener::onClickTimelineStreamStatus,
                        )
                    }
                    IconButton(onClick = uiState.listener::onClickSettings) {
                        Icon(
                            imageVector = AppIcons.Settings,
                            contentDescription = "設定",
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                HomeScreenUiState.Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = uiState.selectedTab == tab,
                        onClick = { uiState.listener.onClickTab(tab) },
                        icon = {
                            when (tab) {
                                HomeScreenUiState.Tab.Home -> Icon(
                                    imageVector = AppIcons.Home,
                                    contentDescription = null,
                                )

                                HomeScreenUiState.Tab.Search -> Icon(
                                    imageVector = AppIcons.Search,
                                    contentDescription = null,
                                )

                                HomeScreenUiState.Tab.User -> ProfileIcon(
                                    iconUrl = uiState.userTabIconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            if (uiState.selectedTab == HomeScreenUiState.Tab.Home) {
                ExtendedFloatingActionButton(
                    onClick = uiState.listener::onClickPost,
                    icon = {
                        Icon(
                            imageVector = AppIcons.Edit,
                            contentDescription = null,
                        )
                    },
                    text = { Text("投稿") },
                )
            }
        },
    ) { paddingValues ->
        val contentModifier = Modifier.fillMaxSize().padding(paddingValues)
        tabStateHolder.SaveableStateProvider(uiState.selectedTab.name) {
            when (uiState.selectedTab) {
                HomeScreenUiState.Tab.Home -> {
                    TimelineTabsContent(
                        uiState = uiState,
                        tabListStates = tabListStates,
                        modifier = contentModifier,
                    )
                }

                HomeScreenUiState.Tab.Search -> {
                    SearchTabContent(
                        listState = tabListStates.search,
                        search = uiState.search,
                        listener = uiState.listener,
                        modifier = contentModifier,
                    )
                }

                HomeScreenUiState.Tab.User -> {
                    UserTabContent(
                        listState = tabListStates.user,
                        profile = uiState.profile,
                        userContentTab = uiState.userContentTab,
                        userPosts = uiState.userPosts,
                        listener = uiState.listener,
                        modifier = contentModifier,
                    )
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
    val addTimelineColumnDialog = uiState.addTimelineColumnDialog
    if (addTimelineColumnDialog != null) {
        AddTimelineColumnDialog(uiState = addTimelineColumnDialog)
    }
}

/**
 * 小さい画面向け。購読先ごとのタイムラインをタブで切り替える
 */
@Composable
private fun TimelineTabsContent(
    uiState: HomeScreenUiState,
    tabListStates: HomeTabListStates,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val selectedIndex = uiState.timelineColumns.indexOfFirst { it.selected }
            if (selectedIndex >= 0) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 0.dp,
                    modifier = Modifier.weight(1f),
                ) {
                    uiState.timelineColumns.forEach { column ->
                        Tab(
                            selected = column.selected,
                            onClick = column.listener::onClickTab,
                            text = { Text(column.title) },
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            IconButton(
                onClick = uiState.listener::onClickAddTimelineColumn,
                enabled = uiState.addTimelineColumnButtonEnabled,
            ) {
                Icon(
                    imageVector = AppIcons.Add,
                    contentDescription = "表示する購読先を追加",
                )
            }
        }
        val selectedColumn = uiState.timelineColumns.firstOrNull { it.selected }
        val emptyMessage = uiState.timelineColumnsEmptyMessage
        val contentModifier = Modifier.fillMaxWidth().weight(1f)
        if (selectedColumn != null) {
            key(selectedColumn.listStateKey) {
                TimelineTabContent(
                    listState = tabListStates.timeline(selectedColumn.listStateKey),
                    timelineItems = selectedColumn.items,
                    refreshing = uiState.timelineRefreshing,
                    footer = uiState.timelineFooter,
                    listener = uiState.listener,
                    modifier = contentModifier,
                )
            }
        } else if (emptyMessage != null) {
            TimelineColumnsEmptyContent(
                message = emptyMessage,
                addButtonEnabled = uiState.addTimelineColumnButtonEnabled,
                onClickAdd = uiState.listener::onClickAddTimelineColumn,
                modifier = contentModifier,
            )
        } else {
            Spacer(modifier = contentModifier)
        }
    }
}

@Composable
internal fun SensitiveImagesVisibilityButton(
    sensitiveImagesBlurred: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        if (sensitiveImagesBlurred) {
            Icon(
                imageVector = AppIcons.VisibilityOff,
                contentDescription = "閲覧注意の画像を隠している。押すと全部表示",
            )
        } else {
            Icon(
                imageVector = AppIcons.Visibility,
                contentDescription = "閲覧注意の画像を全部表示している。押すと隠す",
            )
        }
    }
}

@Composable
internal fun TimelineTabContent(
    listState: LazyListState,
    timelineItems: List<PostItemUiState>,
    refreshing: Boolean,
    footer: HomeScreenUiState.TimelineFooter,
    listener: HomeScreenUiState.Listener,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(listState, listener, timelineItems.size, footer) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            // 購読先で振り分けたカラムは、読んだページにその購読先の投稿が無いと空になる。空でも続きを読まないと止まる
            val nearEnd = lastVisibleIndex == null || lastVisibleIndex >= layoutInfo.totalItemsCount - 5
            nearEnd to layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (nearEnd, _) -> nearEnd }
            .collect { listener.onScrolledToTimelineEnd() }
    }
    val wheelPullToRefreshBlocker = remember { WheelPullToRefreshBlocker() }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = listener::onRefreshTimeline,
        modifier = modifier,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(wheelPullToRefreshBlocker.modifier),
        ) {
            items(timelineItems) { item ->
                PostItem(uiState = item)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            when (footer) {
                HomeScreenUiState.TimelineFooter.None -> Unit

                HomeScreenUiState.TimelineFooter.Loading -> item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is HomeScreenUiState.TimelineFooter.Error -> item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(footer.message, color = MaterialTheme.colorScheme.error)
                        Button(onClick = listener::onClickRetryTimelineLoadMore) { Text("再読み込み") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TimelineStreamStatusButton(
    status: HomeScreenUiState.TimelineStreamStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier) {
        when (status) {
            HomeScreenUiState.TimelineStreamStatus.Receiving -> Icon(
                imageVector = AppIcons.CloudDone,
                contentDescription = "新着投稿を受信中。押すと切断",
            )

            HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect -> ReconnectingIcon(
                contentDescription = "新着投稿の受信に再接続中。押すと切断",
            )

            HomeScreenUiState.TimelineStreamStatus.Disconnected -> Icon(
                imageVector = AppIcons.CloudOff,
                contentDescription = "新着投稿の受信を切断中。押すと再接続",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ReconnectingIcon(contentDescription: String) {
    val rotationDegrees by rememberInfiniteTransition(label = "ReconnectingIcon").animateFloat(
        initialValue = 0f,
        // Sync アイコンの矢印が反時計回りを向いているので、それに合わせて回す
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotationDegrees",
    )
    Icon(
        imageVector = AppIcons.Sync,
        contentDescription = contentDescription,
        modifier = Modifier.rotate(rotationDegrees),
    )
}

@Composable
internal fun SearchTabContent(
    listState: LazyListState,
    search: HomeScreenUiState.Search,
    listener: HomeScreenUiState.Listener,
    modifier: Modifier = Modifier,
) {
    // 一覧の状態は画面で持ち続けるので、検索し直したら前の結果での位置を引き継がない
    val searching = search.result == HomeScreenUiState.SearchResult.Loading
    LaunchedEffect(listState, searching) {
        if (searching) {
            listState.requestScrollToItem(0)
        }
    }
    Column(modifier = modifier) {
        SingleLineTextField(
            value = search.query,
            onValueChange = listener::onSearchQueryChanged,
            onImeAction = listener::onSearch,
            label = "ユーザーを検索",
            kind = SingleLineTextFieldKind.Plain,
            autofill = null,
            formId = null,
            required = false,
            imeAction = ImeAction.Search,
            inputId = SEARCH_QUERY_INPUT_ID,
            inputName = "query",
            enabled = true,
            hasError = false,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when (val result = search.result) {
            HomeScreenUiState.SearchResult.NotSearched -> Unit

            HomeScreenUiState.SearchResult.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            is HomeScreenUiState.SearchResult.Loaded -> {
                if (result.accounts.isEmpty()) {
                    SearchMessage(text = "検索結果はありません")
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(result.accounts) { account ->
                            SearchAccountItem(account = account)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        if (result.loadMoreButtonVisible) {
                            item {
                                TextButton(
                                    onClick = listener::onClickLoadMoreSearchResult,
                                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                                ) {
                                    Text("さらに読み込む")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun UserTabContent(
    listState: LazyGridState,
    profile: HomeScreenUiState.Profile,
    userContentTab: ProfileContentTab,
    userPosts: HomeScreenUiState.UserPosts,
    listener: HomeScreenUiState.Listener,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (profile) {
            HomeScreenUiState.Profile.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is HomeScreenUiState.Profile.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = profile.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = listener::onClickRetryProfile) {
                        Text("再読み込み")
                    }
                }
            }

            is HomeScreenUiState.Profile.Loaded -> {
                LazyVerticalGrid(columns = ProfileGridCells, state = listState, modifier = Modifier.fillMaxSize()) {
                    fullLineItem {
                        UserProfileHeader(profile = profile, listener = listener)
                    }
                    fullLineItem {
                        ProfileContentTabRow(
                            selectedTab = userContentTab,
                            onClickTab = listener::onClickUserContentTab,
                        )
                    }
                    when (userPosts) {
                        HomeScreenUiState.UserPosts.Loading -> fullLineItem {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        HomeScreenUiState.UserPosts.LoadFailed -> fullLineItem {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                Button(onClick = listener::onClickRetryUserPosts) { Text("再読み込み") }
                            }
                        }

                        is HomeScreenUiState.UserPosts.Loaded -> when (userContentTab) {
                            ProfileContentTab.Posts -> {
                                val emptyMessage = userPosts.emptyMessage
                                if (emptyMessage != null) {
                                    fullLineItem { UserTabEmptyMessage(text = emptyMessage) }
                                }
                                items(
                                    items = userPosts.items,
                                    span = { GridItemSpan(maxLineSpan) },
                                ) { post ->
                                    Column {
                                        PostItem(uiState = post)
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }

                            ProfileContentTab.Media -> {
                                val mediaEmptyMessage = userPosts.mediaEmptyMessage
                                if (mediaEmptyMessage != null) {
                                    fullLineItem { UserTabEmptyMessage(text = mediaEmptyMessage) }
                                }
                                items(userPosts.media) { media ->
                                    ProfileMediaGridItem(media = media, modifier = Modifier.padding(1.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.fullLineItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun UserTabEmptyMessage(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun UserProfileHeader(
    profile: HomeScreenUiState.Profile.Loaded,
    listener: HomeScreenUiState.Listener,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileIcon(
                iconUrl = profile.iconUrl,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = profile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = profile.acct,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val summary = profile.summary
        if (summary != null) {
            Text(text = summary, style = MaterialTheme.typography.bodyMedium)
        }
        if (profile.links.isNotEmpty()) {
            ProfileLinks(links = profile.links, modifier = Modifier.fillMaxWidth())
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FollowCount(
                count = profile.followingCount,
                label = "購読",
                onClick = listener::onClickFollowing,
            )
            FollowCount(
                count = profile.followersCount,
                label = "フォロワー",
                onClick = listener::onClickFollowers,
            )
        }
        OutlinedButton(
            onClick = listener::onClickEditProfile,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("プロフィールを編集")
        }
    }
}

@Composable
private fun FollowCount(
    count: String,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
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

@Composable
private fun SearchMessage(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SearchAccountItem(
    account: HomeScreenUiState.SearchAccount,
) {
    val listener = account.listener
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (listener == null) Modifier else Modifier.clickable(onClick = listener::onClick))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileIcon(
            iconUrl = account.iconUrl,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EmojiText(
                text = account.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = Color.Unspecified,
                fontWeight = FontWeight.Bold,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
            )
            Text(
                text = account.acct,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            val followMenu = account.followMenu
            if (followMenu != null) {
                FollowMenu(uiState = followMenu)
            }
        }
    }
}

private fun Screen.Home.Tab.toUiStateTab(): HomeScreenUiState.Tab {
    return when (this) {
        Screen.Home.Tab.Timeline -> HomeScreenUiState.Tab.Home
        Screen.Home.Tab.Search -> HomeScreenUiState.Tab.Search
        Screen.Home.Tab.User -> HomeScreenUiState.Tab.User
    }
}

private fun HomeScreenUiState.Tab.toScreenTab(): Screen.Home.Tab {
    return when (this) {
        HomeScreenUiState.Tab.Home -> Screen.Home.Tab.Timeline
        HomeScreenUiState.Tab.Search -> Screen.Home.Tab.Search
        HomeScreenUiState.Tab.User -> Screen.Home.Tab.User
    }
}

private val HomeScreenUiState.Tab.label: String
    get() = when (this) {
        HomeScreenUiState.Tab.Home -> "ホーム"
        HomeScreenUiState.Tab.Search -> "検索"
        HomeScreenUiState.Tab.User -> "ユーザー"
    }

/**
 * ドラッグの移動量は指を離した後にまとめて処理されることがあるため、離した時点では判定を切り替えない。
 * 次にホイールが回るか押下されるまで、直前の入力の種類を保つ
 */
private class WheelPullToRefreshBlocker : NestedScrollConnection {
    val modifier: Modifier = Modifier
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    when (event.type) {
                        PointerEventType.Scroll -> lastScrollInputIsWheel = true
                        PointerEventType.Press -> lastScrollInputIsWheel = false
                        else -> Unit
                    }
                }
            }
        }
        .nestedScroll(this)

    private var lastScrollInputIsWheel = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val pullOverscrollByWheel = source == NestedScrollSource.UserInput && lastScrollInputIsWheel && available.y > 0f
        return if (pullOverscrollByWheel) Offset(x = 0f, y = available.y) else Offset.Zero
    }
}

/**
 * レールとタイムラインに加えて、投稿などのカラムを 1 つ並べられる幅
 */
private val COLUMNS_LAYOUT_MIN_WIDTH = 840.dp

private const val SEARCH_QUERY_INPUT_ID = "home-search-query"
