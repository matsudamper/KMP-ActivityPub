package net.matsudamper.kmp.activitypub.frontend.screen.admin.unknownobjecttypes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun UnknownObjectTypesScreen(
    navigator: Navigator,
) {
    val viewModel = koinViewModel<UnknownObjectTypesScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : UnknownObjectTypesScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }

                override fun openUrl(url: String) {
                    uriHandler.openUri(url)
                }

                override fun showMessage(message: String) {
                    coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                }
            },
        )
    }

    UnknownObjectTypesContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun UnknownObjectTypesContent(
    uiState: UnknownObjectTypesScreenUiState,
    snackbarHostState: SnackbarHostState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(topBarWindowInsets).padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = uiState.listener::onClickBack) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "戻る",
                    )
                }
                Text(
                    text = "知らない型",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val content = uiState.content) {
                UnknownObjectTypesScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                UnknownObjectTypesScreenUiState.Content.LoadFailed -> {
                    RetryButton(
                        onClickRetry = uiState.listener::onClickRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                UnknownObjectTypesScreenUiState.Content.Empty -> {
                    Text(
                        text = "知らない型はまだ届いていません",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is UnknownObjectTypesScreenUiState.Content.Loaded -> {
                    TypeList(content = content, listener = uiState.listener)
                }
            }
        }
    }
}

@Composable
private fun TypeList(
    content: UnknownObjectTypesScreenUiState.Content.Loaded,
    listener: UnknownObjectTypesScreenUiState.Listener,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, listener) {
        // 件数も比べ、続きを読んでもまだ末尾が見えているときにもう一度知らせる
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            val nearEnd = lastVisibleIndex != null && lastVisibleIndex >= layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
            nearEnd to layoutInfo.totalItemsCount
        }
            .distinctUntilChanged()
            .filter { (nearEnd, _) -> nearEnd }
            .collect { listener.onScrolledToEnd() }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(content.types, key = { it.listKey }) { type ->
            TypeItem(type = type)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        when (val footer = content.footer) {
            UnknownObjectTypesScreenUiState.Footer.None -> Unit

            UnknownObjectTypesScreenUiState.Footer.Loading -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            UnknownObjectTypesScreenUiState.Footer.LoadFailed -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        RetryButton(onClickRetry = listener::onClickRetryLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeItem(
    type: UnknownObjectTypesScreenUiState.UnknownObjectType,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = type.listener::onClickSourceUrl)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = type.typeName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = type.sourceUrl,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = type.seenText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RetryButton(
    onClickRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClickRetry,
        modifier = modifier.padding(16.dp),
    ) {
        Text("再読み込み")
    }
}

/**
 * 末尾からこの数の行が見えたら続きを読む
 */
private const val LOAD_MORE_THRESHOLD = 5
