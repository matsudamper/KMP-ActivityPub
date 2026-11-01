package net.matsudamper.kmp.activitypub.frontend.screen.followlist

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileIcon
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun FollowListScreen(
    navigator: Navigator,
    kind: FollowListKind,
) {
    val viewModel = koinViewModel<FollowListScreenViewModel>(key = kind.name) { parametersOf(kind) }
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : FollowListScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }

                override fun showMessage(message: String) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
        )
    }

    FollowListContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun FollowListContent(
    uiState: FollowListScreenUiState,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = uiState.listener::onClickBack) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "戻る",
                    )
                }
                Text(
                    text = uiState.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val content = uiState.content) {
                FollowListScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is FollowListScreenUiState.Content.Error -> {
                    LoadError(
                        message = content.message,
                        onClickRetry = uiState.listener::onClickRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is FollowListScreenUiState.Content.Loaded -> {
                    val listState = rememberLazyListState()
                    LaunchedEffect(listState, uiState.listener) {
                        // 件数も比べ、続きを読んでもまだ末尾が見えているときにもう一度知らせる
                        snapshotFlow {
                            val layoutInfo = listState.layoutInfo
                            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            val nearEnd = lastVisibleIndex != null && lastVisibleIndex >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
                            nearEnd to layoutInfo.totalItemsCount
                        }
                            .distinctUntilChanged()
                            .filter { (nearEnd, _) -> nearEnd }
                            .collect { uiState.listener.onScrolledToEnd() }
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        val emptyMessage = content.emptyMessage
                        if (emptyMessage != null) {
                            item {
                                Text(
                                    text = emptyMessage,
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(content.accounts) { account ->
                            AccountItem(account = account)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        when (val footer = content.footer) {
                            FollowListScreenUiState.Footer.None -> Unit

                            FollowListScreenUiState.Footer.Loading -> {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }

                            is FollowListScreenUiState.Footer.Error -> {
                                item {
                                    LoadError(
                                        message = footer.message,
                                        onClickRetry = uiState.listener::onClickRetryLoadMore,
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    )
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
private fun LoadError(
    message: String,
    onClickRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
        )
        Button(onClick = onClickRetry) {
            Text("再読み込み")
        }
    }
}

@Composable
private fun AccountItem(
    account: FollowListScreenUiState.Account,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
            Button(onClick = account.listener::onClickFollow, enabled = account.followButtonEnabled) {
                Text(account.followButtonText)
            }
        }
    }
}

/**
 * 末尾からこの数の行が見えたら続きを読む
 */
private const val LOAD_MORE_THRESHOLD = 5
