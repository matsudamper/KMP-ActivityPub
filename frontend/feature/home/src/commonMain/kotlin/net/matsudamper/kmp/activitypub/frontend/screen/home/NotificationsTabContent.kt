package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import net.matsudamper.kmp.activitypub.frontend.ui.AppPullToRefreshBox
import net.matsudamper.kmp.activitypub.frontend.ui.PostItem
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemStyle

@Composable
internal fun NotificationsTabContent(
    listState: LazyListState,
    notifications: HomeScreenUiState.Notifications,
    listener: HomeScreenUiState.Listener,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        when (notifications) {
            HomeScreenUiState.Notifications.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            is HomeScreenUiState.Notifications.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = notifications.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = listener::onClickRetryNotifications) {
                        Text("再読み込み")
                    }
                }
            }

            is HomeScreenUiState.Notifications.Loaded -> {
                LoadedNotifications(
                    listState = listState,
                    notifications = notifications,
                    listener = listener,
                )
            }
        }
    }
}

@Composable
private fun LoadedNotifications(
    listState: LazyListState,
    notifications: HomeScreenUiState.Notifications.Loaded,
    listener: HomeScreenUiState.Listener,
) {
    // 読み直しの間は続きを読まないので、読み直しが終わったときにも末尾にいるかを確かめ直す
    LaunchedEffect(listState, listener, notifications.items.size, notifications.footer, notifications.refreshing) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow false
            lastVisibleIndex >= layoutInfo.totalItemsCount - 5
        }
            .distinctUntilChanged()
            .filter { nearEnd -> nearEnd }
            .collect { listener.onScrolledToNotificationsEnd() }
    }
    AppPullToRefreshBox(
        refreshing = notifications.refreshing,
        onRefresh = listener::onRefreshNotifications,
        modifier = Modifier.fillMaxSize(),
    ) { scrollableModifier ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(scrollableModifier),
        ) {
            val emptyMessage = notifications.emptyMessage
            if (emptyMessage != null) {
                item {
                    Text(
                        text = emptyMessage,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            items(notifications.items) { item ->
                PostItem(uiState = item, style = PostItemStyle.Timeline)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            when (val footer = notifications.footer) {
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
                        Button(onClick = listener::onClickRetryLoadMoreNotifications) { Text("再読み込み") }
                    }
                }
            }
        }
    }
}
