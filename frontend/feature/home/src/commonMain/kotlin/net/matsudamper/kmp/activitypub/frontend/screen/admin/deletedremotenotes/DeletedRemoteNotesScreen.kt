package net.matsudamper.kmp.activitypub.frontend.screen.admin.deletedremotenotes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
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
internal fun DeletedRemoteNotesScreen(
    navigator: Navigator,
) {
    val viewModel = koinViewModel<DeletedRemoteNotesScreenViewModel>()
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel.eventHandler, navigator) {
        viewModel.eventHandler.collect(
            object : DeletedRemoteNotesScreenViewModel.Event {
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

    DeletedRemoteNotesContent(uiState = uiState, snackbarHostState = snackbarHostState)
}

@Composable
internal fun DeletedRemoteNotesContent(
    uiState: DeletedRemoteNotesScreenUiState,
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
                    text = "削除済みの投稿",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = uiState.listener::onClickPurgeAll,
                    enabled = uiState.purgeAllButtonEnabled,
                ) {
                    Text(text = "全て削除")
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            when (val content = uiState.content) {
                DeletedRemoteNotesScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                DeletedRemoteNotesScreenUiState.Content.LoadFailed -> {
                    RetryButton(
                        onClickRetry = uiState.listener::onClickRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                DeletedRemoteNotesScreenUiState.Content.Empty -> {
                    Text(
                        text = "削除済みの投稿はありません",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is DeletedRemoteNotesScreenUiState.Content.Loaded -> {
                    NoteList(loaded = content, listener = uiState.listener)
                }
            }
        }
    }
    val purgeAllDialog = uiState.purgeAllDialog
    if (purgeAllDialog != null) {
        PurgeAllDialog(uiState = purgeAllDialog)
    }
}

@Composable
private fun NoteList(
    loaded: DeletedRemoteNotesScreenUiState.Content.Loaded,
    listener: DeletedRemoteNotesScreenUiState.Listener,
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
    LazyColumn(
        state = listState,
        modifier = Modifier.widthIn(max = LIST_MAX_WIDTH).fillMaxSize(),
    ) {
        items(loaded.notes, key = { it.listKey }) { note ->
            NoteItem(note = note)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        when (loaded.footer) {
            DeletedRemoteNotesScreenUiState.Footer.None -> Unit

            DeletedRemoteNotesScreenUiState.Footer.Loading -> {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            DeletedRemoteNotesScreenUiState.Footer.LoadFailed -> {
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
private fun NoteItem(
    note: DeletedRemoteNotesScreenUiState.DeletedNote,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = note.actorText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = note.contentText,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = note.objectUri,
                modifier = Modifier.clickable(onClick = note.listener::onClickObjectUri),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = note.dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = note.listener::onClickPurge,
            enabled = note.purgeButtonEnabled,
        ) {
            Icon(
                imageVector = AppIcons.Delete,
                contentDescription = "削除",
            )
        }
    }
}

@Composable
private fun PurgeAllDialog(
    uiState: DeletedRemoteNotesScreenUiState.PurgeAllDialog,
) {
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text("削除済みの投稿を全て削除") },
        text = { Text(uiState.message) },
        confirmButton = {
            TextButton(
                onClick = uiState.listener::onClickPurge,
                enabled = uiState.buttonsEnabled,
            ) {
                Text("削除")
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

private val LIST_MAX_WIDTH = 720.dp
