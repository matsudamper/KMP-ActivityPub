package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIcon
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextFieldKind
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
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

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : HomeScreenViewModel.Event {
                override fun navigateToTab(tab: HomeScreenUiState.Tab) {
                    navigator.selectHomeTab(tab.toScreenTab())
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
            },
        )
    }

    LaunchedEffect(tab, uiState.listener) {
        uiState.listener.onTabChanged(tab.toUiStateTab())
    }

    LaunchedEffect(uiState.listener) {
        uiState.listener.onScreenShown()
    }
    HomeContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun HomeContent(
    uiState: HomeScreenUiState,
    snackbarHostState: SnackbarHostState,
) {
    val tabStateHolder = rememberSaveableStateHolder()
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
                    TimelineTabContent(
                        timelineItems = uiState.timelineItems,
                        refreshing = uiState.timelineRefreshing,
                        loadMoreVisible = uiState.timelineLoadMoreVisible,
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

                HomeScreenUiState.Tab.User -> {
                    UserTabContent(
                        profile = uiState.profile,
                        listener = uiState.listener,
                        modifier = contentModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineTabContent(
    timelineItems: List<HomeScreenUiState.TimelineItem>,
    refreshing: Boolean,
    loadMoreVisible: Boolean,
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
            if (loadMoreVisible) {
                item {
                    TextButton(onClick = listener::onLoadMoreTimeline, modifier = Modifier.fillMaxWidth()) { Text("さらに読み込む") }
                }
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
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
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
private fun UserTabContent(
    profile: HomeScreenUiState.Profile,
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FollowCount(
                            count = profile.followingCount,
                            label = "フォロー",
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
            val followButtonText = account.followButtonText
            if (followButtonText != null) {
                Button(onClick = account.onClickFollow, enabled = account.followButtonEnabled) {
                    Text(followButtonText)
                }
            }
        }
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

private const val SEARCH_QUERY_INPUT_ID = "home-search-query"
