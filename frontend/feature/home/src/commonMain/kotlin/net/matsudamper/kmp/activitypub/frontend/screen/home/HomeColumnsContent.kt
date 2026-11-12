package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialog
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIcon
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * 横に広い画面向け。タブを切り替えずに、レールから開いたカラムを 1 つだけタイムラインの左に出す。
 * タイムラインは購読先ごとのカラムを並べ、右端のボタンで足す
 *
 * @param postColumn 投稿の画面は別の ViewModel で動くので、外から差し込む
 */
@Composable
internal fun HomeColumnsContent(
    uiState: HomeScreenUiState,
    snackbarHostState: SnackbarHostState,
    tabListStates: HomeTabListStates,
    postColumn: @Composable (Modifier) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                HomeRail(uiState = uiState)
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxHeight().weight(1f).horizontalScroll(rememberScrollState()),
                ) {
                    val column = uiState.openedColumn
                    if (column != null) {
                        val columnModifier = Modifier.width(uiState.openedColumnWidth).fillMaxHeight()
                        when (column) {
                            HomeScreenUiState.Column.Post -> postColumn(columnModifier)

                            HomeScreenUiState.Column.Search -> HomeColumn(
                                title = "検索",
                                onClickHeader = { uiState.listener.onClickColumnHeader(HomeScreenUiState.Tab.Search) },
                                headerActions = { ColumnCloseButton(title = "検索", onClick = { uiState.listener.onClickCloseColumn(column) }) },
                                modifier = columnModifier,
                            ) { contentModifier ->
                                SearchTabContent(
                                    listState = tabListStates.search,
                                    search = uiState.search,
                                    listener = uiState.listener,
                                    modifier = contentModifier,
                                )
                            }

                            HomeScreenUiState.Column.User -> HomeColumn(
                                title = "ユーザー",
                                onClickHeader = { uiState.listener.onClickColumnHeader(HomeScreenUiState.Tab.User) },
                                headerActions = { ColumnCloseButton(title = "ユーザー", onClick = { uiState.listener.onClickCloseColumn(column) }) },
                                modifier = columnModifier,
                            ) { contentModifier ->
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
                        ColumnResizeDivider(
                            onResize = uiState.listener::onResizeOpenedColumn,
                            onResizeEnd = uiState.listener::onResizeOpenedColumnEnd,
                        )
                    }
                    val reorderState = rememberHorizontalReorderState()
                    SideEffect { reorderState.retainItems(uiState.timelineColumns.size) }
                    uiState.timelineColumns.forEachIndexed { index, timelineColumn ->
                        key(timelineColumn.listStateKey) {
                            HomeColumn(
                                title = timelineColumn.title,
                                onClickHeader = timelineColumn.listener::onClickHeader,
                                headerActions = { TimelineColumnSettingsButton(column = timelineColumn) },
                                headerModifier = Modifier.horizontalReorderHandle(
                                    state = reorderState,
                                    index = index,
                                    onMove = uiState.listener::onMoveTimelineColumn,
                                ),
                                modifier = Modifier
                                    .width(timelineColumn.width)
                                    .fillMaxHeight()
                                    .horizontalReorderItem(state = reorderState, index = index, dropIndicatorColor = MaterialTheme.colorScheme.primary)
                                    .background(MaterialTheme.colorScheme.background),
                            ) { contentModifier ->
                                TimelineTabContent(
                                    listState = tabListStates.timeline(timelineColumn.listStateKey),
                                    timelineItems = timelineColumn.items,
                                    refreshing = uiState.timelineRefreshing,
                                    footer = timelineColumn.footer,
                                    loadMoreAtEndEnabled = true,
                                    listener = uiState.listener,
                                    columnListener = timelineColumn.listener,
                                    modifier = contentModifier,
                                )
                            }
                            ColumnResizeDivider(
                                onResize = timelineColumn.listener::onResize,
                                onResizeEnd = timelineColumn.listener::onResizeEnd,
                            )
                        }
                    }
                    val emptyMessage = uiState.timelineColumnsEmptyMessage
                    if (emptyMessage != null) {
                        TimelineColumnsEmptyContent(
                            message = emptyMessage,
                            addButtonEnabled = uiState.addTimelineColumnButtonEnabled,
                            onClickAdd = uiState.listener::onClickAddTimelineColumn,
                            modifier = Modifier.width(COLUMN_WIDTH).fillMaxHeight(),
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    IconButton(
                        onClick = uiState.listener::onClickAddTimelineColumn,
                        enabled = uiState.addTimelineColumnButtonEnabled,
                        modifier = Modifier.windowInsetsPadding(topBarWindowInsets).padding(4.dp),
                    ) {
                        Icon(
                            imageVector = AppIcons.Add,
                            contentDescription = "カラムを追加",
                        )
                    }
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
    val deleteNoteDialog = uiState.deleteNoteDialog
    if (deleteNoteDialog != null) {
        DeleteNoteDialog(uiState = deleteNoteDialog)
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

@Composable
private fun HomeRail(uiState: HomeScreenUiState) {
    NavigationRail {
        uiState.railItems.forEach { item ->
            ColumnToggleButton(
                item = item,
                userIconUrl = uiState.userTabIconUrl,
                onClick = { uiState.listener.onClickRailItem(item.column) },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        SensitiveImagesVisibilityButton(
            sensitiveImagesBlurred = uiState.sensitiveImagesBlurred,
            onClick = uiState.listener::onClickSensitiveImagesVisibility,
            modifier = Modifier.size(width = RAIL_BUTTON_WIDTH, height = 48.dp),
        )
        TimelineStreamStatusButton(
            status = uiState.timelineStreamStatus,
            onClick = uiState.listener::onClickTimelineStreamStatus,
            modifier = Modifier.size(width = RAIL_BUTTON_WIDTH, height = 48.dp),
        )
        IconButton(
            onClick = uiState.listener::onClickSettings,
            modifier = Modifier.size(width = RAIL_BUTTON_WIDTH, height = 48.dp),
        ) {
            Icon(
                imageVector = AppIcons.Settings,
                contentDescription = "設定",
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
    }
}

/**
 * 押すたびにカラムを開け閉めするので、開いているかを塗りで見せる。
 * 塗りを押せる範囲と同じ大きさにして、どこまで押せるかを見せる
 */
@Composable
private fun ColumnToggleButton(
    item: HomeScreenUiState.RailItem,
    userIconUrl: String?,
    onClick: () -> Unit,
) {
    val containerColor = if (item.selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (item.selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Column(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .size(width = RAIL_BUTTON_WIDTH, height = 64.dp)
            .clip(MaterialTheme.shapes.large)
            .background(containerColor)
            .toggleable(
                value = item.selected,
                role = Role.Checkbox,
                onValueChange = { onClick() },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            when (item.column) {
                HomeScreenUiState.Column.Post -> Icon(
                    imageVector = AppIcons.Edit,
                    contentDescription = null,
                )

                HomeScreenUiState.Column.Search -> Icon(
                    imageVector = AppIcons.Search,
                    contentDescription = null,
                )

                HomeScreenUiState.Column.User -> ProfileIcon(
                    iconUrl = userIconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = item.column.label,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * @param headerModifier 見出しを長押しして並べ替えるカラムで、見出しに付ける
 */
@Composable
private fun HomeColumn(
    title: String,
    onClickHeader: () -> Unit,
    headerActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    headerModifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = headerModifier
                .fillMaxWidth()
                .clickable(onClick = onClickHeader)
                .windowInsetsPadding(topBarWindowInsets)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            headerActions()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content(Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun ColumnCloseButton(title: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = AppIcons.Close,
            contentDescription = "${title}を閉じる",
        )
    }
}

private val HomeScreenUiState.Column.label: String
    get() = when (this) {
        HomeScreenUiState.Column.Post -> "投稿"
        HomeScreenUiState.Column.Search -> "検索"
        HomeScreenUiState.Column.User -> "ユーザー"
    }

private val COLUMN_WIDTH = 400.dp
private val RAIL_BUTTON_WIDTH = 72.dp
