package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextFieldKind
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun HomeScreen(
    navigator: Navigator,
) {
    val viewModel = koinViewModel<HomeScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : HomeScreenViewModel.Event {
                override fun navigateToPost() {
                    navigator.navigate(Screen.Post)
                }

                override fun navigateToSettings() {
                    navigator.navigate(Screen.Settings)
                }
            },
        )
    }

    HomeContent(uiState = uiState)
}

@Composable
internal fun HomeContent(
    uiState: HomeScreenUiState,
) {
    Scaffold(
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uiState.selectedTab.label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
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
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
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
        when (uiState.selectedTab) {
            HomeScreenUiState.Tab.Home -> {
                TimelineTabContent(
                    timelineItems = uiState.timelineItems,
                    refreshing = uiState.timelineRefreshing,
                    listener = uiState.listener,
                    modifier = contentModifier,
                )
            }

            HomeScreenUiState.Tab.Search -> {
                SearchTabContent(
                    search = uiState.search,
                    listener = uiState.listener,
                    modifier = contentModifier,
                )
            }
        }
    }
}

@Composable
private fun TimelineTabContent(
    timelineItems: List<HomeScreenUiState.TimelineItem>,
    refreshing: Boolean,
    listener: HomeScreenUiState.Listener,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = listener::onRefreshTimeline,
        modifier = modifier,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(timelineItems) { item ->
                TimelineItem(item = item)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun SearchTabContent(
    search: HomeScreenUiState.Search,
    listener: HomeScreenUiState.Listener,
    modifier: Modifier = Modifier,
) {
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
        if (search.accounts.isEmpty()) {
            Text(
                text = "検索結果はありません",
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(search.accounts) { account ->
                    SearchAccountItem(account = account)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun SearchAccountItem(
    account: HomeScreenUiState.SearchAccount,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = account.displayName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = account.acct,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun TimelineItem(
    item: HomeScreenUiState.TimelineItem,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = item.acct,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = item.postedAt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = item.body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private val HomeScreenUiState.Tab.label: String
    get() = when (this) {
        HomeScreenUiState.Tab.Home -> "ホーム"
        HomeScreenUiState.Tab.Search -> "検索"
    }

private val HomeScreenUiState.Tab.icon: ImageVector
    get() = when (this) {
        HomeScreenUiState.Tab.Home -> AppIcons.Home
        HomeScreenUiState.Tab.Search -> AppIcons.Search
    }

private const val SEARCH_QUERY_INPUT_ID = "home-search-query"
