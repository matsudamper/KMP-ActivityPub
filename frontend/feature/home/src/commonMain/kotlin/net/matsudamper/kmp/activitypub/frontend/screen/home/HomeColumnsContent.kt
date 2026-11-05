package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIcon
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * 横に広い画面向け。タブを切り替えずに、レールから開いたカラムをタイムラインの左に並べる
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
                    uiState.openedColumns.forEach { column ->
                        val columnModifier = Modifier.width(COLUMN_WIDTH).fillMaxHeight()
                        when (column) {
                            HomeScreenUiState.Column.Post -> postColumn(columnModifier)

                            HomeScreenUiState.Column.Search -> HomeColumn(
                                title = "検索",
                                onClickClose = { uiState.listener.onClickCloseColumn(column) },
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
                                onClickClose = { uiState.listener.onClickCloseColumn(column) },
                                modifier = columnModifier,
                            ) { contentModifier ->
                                UserTabContent(
                                    listState = tabListStates.user,
                                    profile = uiState.profile,
                                    userPosts = uiState.userPosts,
                                    listener = uiState.listener,
                                    modifier = contentModifier,
                                )
                            }
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                    HomeColumn(
                        title = "タイムライン",
                        onClickClose = null,
                        modifier = Modifier.width(COLUMN_WIDTH).fillMaxHeight(),
                    ) { contentModifier ->
                        TimelineTabContent(
                            listState = tabListStates.timeline,
                            timelineItems = uiState.timelineItems,
                            refreshing = uiState.timelineRefreshing,
                            footer = uiState.timelineFooter,
                            listener = uiState.listener,
                            modifier = contentModifier,
                        )
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun HomeRail(uiState: HomeScreenUiState) {
    NavigationRail {
        uiState.railItems.forEach { item ->
            NavigationRailItem(
                selected = item.selected,
                onClick = { uiState.listener.onClickRailItem(item.column) },
                icon = {
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
                            iconUrl = uiState.userTabIconUrl,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                },
                label = { Text(item.column.label) },
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = uiState.listener::onClickSettings) {
            Icon(
                imageVector = AppIcons.Settings,
                contentDescription = "設定",
            )
        }
        TimelineStreamStatusButton(
            status = uiState.timelineStreamStatus,
            onClick = uiState.listener::onClickTimelineStreamStatus,
        )
        SensitiveImagesVisibilityButton(
            sensitiveImagesBlurred = uiState.sensitiveImagesBlurred,
            onClick = uiState.listener::onClickSensitiveImagesVisibility,
        )
        Spacer(modifier = Modifier.size(8.dp))
    }
}

/**
 * @param onClickClose 閉じられないカラムは null
 */
@Composable
private fun HomeColumn(
    title: String,
    onClickClose: (() -> Unit)?,
    modifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
            if (onClickClose != null) {
                IconButton(onClick = onClickClose) {
                    Icon(
                        imageVector = AppIcons.Close,
                        contentDescription = "${title}を閉じる",
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content(Modifier.fillMaxWidth().weight(1f))
    }
}

private val HomeScreenUiState.Column.label: String
    get() = when (this) {
        HomeScreenUiState.Column.Post -> "投稿"
        HomeScreenUiState.Column.Search -> "検索"
        HomeScreenUiState.Column.User -> "ユーザー"
    }

private val COLUMN_WIDTH = 400.dp
