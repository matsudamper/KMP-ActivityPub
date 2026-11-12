package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
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
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemStyle
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
            // 出ていないカラムのリストはスクロールが終わらないので、タイムラインは scrollTimelineColumnToTop でカラムを選んで戻す
            HomeScreenUiState.Tab.Home -> Unit

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
    val timelineColumnEditDialog = uiState.timelineColumnEditDialog
    if (timelineColumnEditDialog != null) {
        TimelineColumnEditDialog(uiState = timelineColumnEditDialog)
    }
    val timelineColumnDeleteDialog = uiState.timelineColumnDeleteDialog
    if (timelineColumnDeleteDialog != null) {
        TimelineColumnDeleteDialog(uiState = timelineColumnDeleteDialog)
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
    val selectedIndex = uiState.timelineColumns.indexOfFirst { it.selected }
    // タブの下の線をスワイプの途中にも追従させるため、タブ行とページで同じ状態を使う
    val pagerState = rememberPagerState(initialPage = selectedIndex.coerceAtLeast(0)) { uiState.timelineColumns.size }
    Column(modifier = modifier) {
        // メニューボタンの下まで線を通すため、タブ行の線は消して行全体に引く
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectedIndex >= 0) {
                val reorderState = rememberHorizontalReorderState()
                SideEffect { reorderState.retainItems(uiState.timelineColumns.size) }
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 0.dp,
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
                                val indicatorPosition = pagerIndicatorPosition(
                                    tabPositions = tabPositions,
                                    currentPage = pagerState.currentPage,
                                    currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                                )
                                val indicatorWidth = indicatorPosition.width.roundToPx()
                                val placeable = measurable.measure(
                                    constraints.copy(minWidth = indicatorWidth, maxWidth = indicatorWidth),
                                )
                                // タブ行は下線を選んでいるタブの幅の中で中央に寄せて置くので、その分を差し引く
                                val selectedTabWidth = tabPositions.getOrNull(selectedIndex)?.width?.roundToPx() ?: 0
                                val offsetByTabRow = maxOf(0, (selectedTabWidth - placeable.width) / 2)
                                layout(placeable.width, placeable.height) {
                                    placeable.place(x = indicatorPosition.left.roundToPx() - offsetByTabRow, y = 0)
                                }
                            },
                            width = Dp.Unspecified,
                        )
                    },
                    divider = {},
                    modifier = Modifier.weight(1f),
                ) {
                    uiState.timelineColumns.forEachIndexed { index, column ->
                        Tab(
                            selected = column.selected,
                            onClick = column.listener::onClickTab,
                            text = { Text(column.title) },
                            modifier = Modifier
                                .horizontalReorderItem(state = reorderState, index = index, dropIndicatorColor = MaterialTheme.colorScheme.primary)
                                .background(MaterialTheme.colorScheme.surface)
                                .horizontalReorderHandle(
                                    state = reorderState,
                                    index = index,
                                    onMove = uiState.listener::onMoveTimelineColumn,
                                ),
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            val selectedColumn = uiState.timelineColumns.getOrNull(selectedIndex)
            TimelineTabsMenuButton(
                selectedColumn = selectedColumn,
                addItemEnabled = uiState.addTimelineColumnButtonEnabled,
                onClickAdd = uiState.listener::onClickAddTimelineColumn,
            )
        }
        HorizontalDivider()
        val emptyMessage = uiState.timelineColumnsEmptyMessage
        val contentModifier = Modifier.fillMaxWidth().weight(1f)
        if (selectedIndex >= 0) {
            TimelineColumnsPager(
                uiState = uiState,
                pagerState = pagerState,
                selectedIndex = selectedIndex,
                tabListStates = tabListStates,
                modifier = contentModifier,
            )
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
private fun TimelineColumnsPager(
    uiState: HomeScreenUiState,
    pagerState: PagerState,
    selectedIndex: Int,
    tabListStates: HomeTabListStates,
    modifier: Modifier = Modifier,
) {
    val columns = uiState.timelineColumns
    LaunchedEffect(pagerState, selectedIndex) {
        // 途中で止まったアニメーションのずれが残るので、ページの番号だけでなくずれも見る
        val pageSettledOnSelected = pagerState.currentPage == selectedIndex && pagerState.currentPageOffsetFraction == 0f
        if (!pageSettledOnSelected) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }
    val latestColumns by rememberUpdatedState(columns)
    LaunchedEffect(pagerState) {
        // 元のページに戻って止まったときも同期するため、スクロール中は null を挟んで止まるたびに流す
        snapshotFlow { if (pagerState.isScrollInProgress) null else pagerState.settledPage }.collect { page ->
            val column = page?.let { latestColumns.getOrNull(it) }
            if (column != null && !column.selected) {
                column.listener.onClickTab()
            }
        }
    }
    val defaultPageNestedScrollConnection = PagerDefaults.pageNestedScrollConnection(pagerState, Orientation.Horizontal)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val pageNestedScrollConnection = remember(pagerState, defaultPageNestedScrollConnection, density, layoutDirection) {
        FlingVelocityKeepingPageNestedScrollConnection(
            pagerState = pagerState,
            defaultConnection = defaultPageNestedScrollConnection,
            pageChangeVelocityThreshold = with(density) { PageChangeVelocityThreshold.toPx() },
            layoutDirection = layoutDirection,
        )
    }
    var pagerSwipeEnabled by remember { mutableStateOf(true) }
    HorizontalPager(
        state = pagerState,
        key = { page -> columns[page].listStateKey },
        pageNestedScrollConnection = pageNestedScrollConnection,
        userScrollEnabled = pagerSwipeEnabled,
        modifier = modifier.disablePagerSwipeWhileVerticalDrag(
            onPagerSwipeEnabledChange = { pagerSwipeEnabled = it },
        ),
    ) { page ->
        val column = columns[page]
        TimelineTabContent(
            listState = tabListStates.timeline(column.listStateKey),
            timelineItems = column.items,
            refreshing = uiState.timelineRefreshing,
            footer = uiState.timelineFooter,
            // 隣のページはスワイプの途中から作られるので、見ていないカラムのために続きを読まない
            loadMoreAtEndEnabled = column.selected,
            listener = uiState.listener,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * 縦に動かし始めた指は、離すまで横に動かしてもページを切り替えない。
 * 縦の列が動かしている最中も、ページャーは横の移動だけを見てドラッグを始めてしまう
 */
private fun Modifier.disablePagerSwipeWhileVerticalDrag(
    onPagerSwipeEnabledChange: (Boolean) -> Unit,
): Modifier = pointerInput(onPagerSwipeEnabledChange) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var totalDrag = Offset.Zero
        var dragDirectionDecided = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (!dragDirectionDecided && change != null) {
                totalDrag += change.positionChangeIgnoreConsumed()
                val verticalDrag = abs(totalDrag.y) > viewConfiguration.touchSlop && abs(totalDrag.y) > abs(totalDrag.x)
                val horizontalDrag = abs(totalDrag.x) > viewConfiguration.touchSlop && abs(totalDrag.x) >= abs(totalDrag.y)
                if (verticalDrag) {
                    onPagerSwipeEnabledChange(false)
                }
                dragDirectionDecided = verticalDrag || horizontalDrag
            }
        } while (event.changes.any { it.pressed })
        onPagerSwipeEnabledChange(true)
    }
}

/** ページャーが指を払ったと見なす速さの標準値に合わせる */
private val PageChangeVelocityThreshold = 400.dp

/**
 * 画像や OGP の横に並ぶ列から始めたスワイプでも、本文から始めたときと同じく払った速さでページを切り替える。
 * 標準の接続は子のスクロールから渡された速さを捨てるので、半分を超えて動かさないと切り替わらない
 */
private class FlingVelocityKeepingPageNestedScrollConnection(
    private val pagerState: PagerState,
    private val defaultConnection: NestedScrollConnection,
    private val pageChangeVelocityThreshold: Float,
    private val layoutDirection: LayoutDirection,
) : NestedScrollConnection by defaultConnection {
    override suspend fun onPreFling(available: Velocity): Velocity {
        val pageMovedByDrag = abs(pagerState.currentPageOffsetFraction) > 1e-6f
        if (!pageMovedByDrag) return defaultConnection.onPreFling(available)
        val pagePosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
        val velocityTowardNextPage = if (layoutDirection == LayoutDirection.Ltr) -available.x else available.x
        val targetPage = when {
            velocityTowardNextPage > pageChangeVelocityThreshold -> ceil(pagePosition)
            velocityTowardNextPage < -pageChangeVelocityThreshold -> floor(pagePosition)
            else -> round(pagePosition)
        }.toInt().coerceIn(0, pagerState.pageCount - 1)
        pagerState.animateScrollToPage(targetPage)
        // ページを動かしたので、子の列には払った速さを渡さない
        return available
    }
}

private data class TabIndicatorPosition(
    val left: Dp,
    val width: Dp,
)

/**
 * スワイプ中のページのずれに合わせて、今のタブと向かう先のタブの間に下線を置く
 */
private fun pagerIndicatorPosition(
    tabPositions: List<TabPosition>,
    currentPage: Int,
    currentPageOffsetFraction: Float,
): TabIndicatorPosition {
    val currentTab = tabPositions.getOrNull(currentPage) ?: return TabIndicatorPosition(left = 0.dp, width = 0.dp)
    val targetPage = if (currentPageOffsetFraction > 0f) currentPage + 1 else currentPage - 1
    val targetTab = tabPositions.getOrNull(targetPage) ?: currentTab
    val fraction = abs(currentPageOffsetFraction)
    val width = lerp(currentTab.contentWidth, targetTab.contentWidth, fraction)
    val center = lerp(
        currentTab.left + currentTab.width / 2,
        targetTab.left + targetTab.width / 2,
        fraction,
    )
    return TabIndicatorPosition(left = center - width / 2, width = width)
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
    loadMoreAtEndEnabled: Boolean,
    listener: HomeScreenUiState.Listener,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(listState, listener, timelineItems.size, footer, loadMoreAtEndEnabled) {
        if (!loadMoreAtEndEnabled) return@LaunchedEffect
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
                PostItem(uiState = item, style = PostItemStyle.Timeline)
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
                                        PostItem(uiState = post, style = PostItemStyle.Timeline)
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
internal val COLUMNS_LAYOUT_MIN_WIDTH = 840.dp

private const val SEARCH_QUERY_INPUT_ID = "home-search-query"
