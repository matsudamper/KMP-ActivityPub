package net.matsudamper.kmp.activitypub.frontend.screen.postrevisions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.EmojiText
import net.matsudamper.kmp.activitypub.frontend.ui.PostImageCard
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun PostRevisionsScreen(
    postId: String,
    navigator: Navigator,
) {
    val viewModel = koinViewModel<PostRevisionsScreenViewModel>(key = postId) { parametersOf(postId) }
    val uiState by viewModel.uiStateFlow.collectAsState()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(viewModel.eventHandler, navigator, uriHandler) {
        viewModel.eventHandler.collect(
            object : PostRevisionsScreenViewModel.Event {
                override fun navigateBack() {
                    navigator.back()
                }

                override fun navigateToUserProfile(username: String) {
                    navigator.navigate(Screen.UserProfile(username = username))
                }

                override fun navigateToImage(urls: List<String>, index: Int) {
                    navigator.navigate(Screen.Image(urls = urls, initialIndex = index))
                }

                override fun openUrl(url: String) {
                    uriHandler.openUri(url)
                }
            },
        )
    }

    PostRevisionsContent(uiState = uiState)
}

@Composable
internal fun PostRevisionsContent(
    uiState: PostRevisionsScreenUiState,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
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
                    text = "編集履歴",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (val content = uiState.content) {
                PostRevisionsScreenUiState.Content.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is PostRevisionsScreenUiState.Content.Error -> {
                    LoadError(
                        message = content.message,
                        onClickRetry = uiState.listener::onClickRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                is PostRevisionsScreenUiState.Content.Loaded -> {
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
                        items(content.revisions, key = { it.listKey }) { revision ->
                            RevisionItem(revision = revision)
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        when (val footer = content.footer) {
                            PostRevisionsScreenUiState.Footer.None -> Unit

                            PostRevisionsScreenUiState.Footer.Loading -> {
                                item {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }

                            is PostRevisionsScreenUiState.Footer.Error -> {
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
private fun RevisionItem(
    revision: PostRevisionsScreenUiState.Revision,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = revision.revisedAt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (revision.currentLabelVisible) {
                Text(
                    text = "今の版",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        EmojiText(
            text = revision.body,
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Unspecified,
            fontWeight = null,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
        )
        if (revision.images.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(revision.images) { image ->
                    PostImageCard(image = image)
                }
            }
        }
    }
}

/**
 * 末尾からこの数の行が見えたら続きを読む
 */
private const val LOAD_MORE_THRESHOLD = 5
