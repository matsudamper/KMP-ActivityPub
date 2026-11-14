package net.matsudamper.kmp.activitypub.frontend.screen.postdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.topBarWindowInsets
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialog
import net.matsudamper.kmp.activitypub.frontend.ui.PostItem
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemStyle
import net.matsudamper.kmp.activitypub.frontend.ui.URL_COPIED_MESSAGE
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import net.matsudamper.kmp.activitypub.frontend.ui.rememberUrlSharer
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
internal fun PostDetailScreen(
    postId: String,
    navigator: Navigator,
    layout: PostDetailLayout,
) {
    val viewModel = koinViewModel<PostDetailScreenViewModel>(key = postId) { parametersOf(postId) }
    val uiState by viewModel.uiStateFlow.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val urlSharer = rememberUrlSharer(
        onCopiedToClipboard = { coroutineScope.launch { snackbarHostState.showSnackbar(URL_COPIED_MESSAGE) } },
    )
    LaunchedEffect(viewModel.eventHandler, navigator, uriHandler) {
        viewModel.eventHandler.collect(object : PostDetailScreenViewModel.Event {
            override fun navigateBack() {
                navigator.back()
            }

            override fun showMessage(message: String) {
                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
            }

            override fun navigateToUserProfile(username: String) {
                navigator.navigate(Screen.UserProfile(username = username))
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
        })
    }
    PostDetailContent(uiState = uiState, snackbarHostState = snackbarHostState, layout = layout)
}

internal enum class PostDetailLayout {
    FullScreen,

    /** 投稿が短くても下に空きを残さない */
    Dialog,
}

@Composable
internal fun PostDetailContent(
    uiState: PostDetailScreenUiState,
    snackbarHostState: SnackbarHostState,
    layout: PostDetailLayout,
) {
    val fillsScreen = when (layout) {
        PostDetailLayout.FullScreen -> true
        PostDetailLayout.Dialog -> false
    }
    // Scaffold を使わないので、文字やアイコンの色が背景に合うよう Surface で色を渡す
    Surface(
        modifier = if (fillsScreen) Modifier.fillMaxSize() else Modifier,
        color = MaterialTheme.colorScheme.background,
    ) {
        Box {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(topBarWindowInsets).padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = uiState.listener::onClickBack) {
                        Icon(imageVector = AppIcons.ArrowBack, contentDescription = "戻る")
                    }
                    Text(text = "投稿", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = fillsScreen)
                        .windowInsetsPadding(contentWindowInsets),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = fillsScreen)
                            .heightIn(min = ContentMinHeight),
                    ) {
                        when (val content = uiState.content) {
                            PostDetailScreenUiState.Content.Loading -> {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }

                            is PostDetailScreenUiState.Content.Loaded -> {
                                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                    item {
                                        PostItem(uiState = content.post, style = PostItemStyle.Detail)
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }

                            PostDetailScreenUiState.Content.NotFound -> {
                                Text(text = "投稿が見つかりません", modifier = Modifier.align(Alignment.Center))
                            }

                            PostDetailScreenUiState.Content.Error -> {
                                Column(
                                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text(text = "投稿を読み込めませんでした")
                                    Button(onClick = uiState.listener::onClickRetry) { Text("再読み込み") }
                                }
                            }
                        }
                        // 下のメンションの入力欄に被らないよう、投稿の領域の下端に出す
                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                    val loadedContent = uiState.content as? PostDetailScreenUiState.Content.Loaded
                    if (loadedContent != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        MentionBox(uiState = loadedContent.mentionBox)
                    }
                }
            }
        }
    }
    val deleteNoteDialog = uiState.deleteNoteDialog
    if (deleteNoteDialog != null) {
        DeleteNoteDialog(uiState = deleteNoteDialog)
    }
}

@Composable
private fun MentionBox(uiState: PostDetailScreenUiState.MentionBox) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = uiState.text,
            onValueChange = uiState.listener::onTextChange,
            modifier = Modifier.weight(1f),
            enabled = uiState.textFieldEnabled,
            maxLines = MENTION_BOX_MAX_LINES,
            placeholder = { Text(text = uiState.placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
        Button(onClick = uiState.listener::onClickSend, enabled = uiState.sendButtonEnabled) {
            Text(text = "送信")
        }
    }
}

/**
 * 長く書いても投稿が隠れないよう、入力欄の伸びを抑える
 */
private const val MENTION_BOX_MAX_LINES = 4

private val contentWindowInsets: WindowInsets
    @Composable
    get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

/**
 * 読み込み中やエラーの表示で、ダイアログが潰れないようにする
 */
private val ContentMinHeight = 200.dp
