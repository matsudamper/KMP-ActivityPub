package net.matsudamper.kmp.activitypub.frontend.screen.reaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import coil3.compose.AsyncImage
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextField
import net.matsudamper.kmp.activitypub.frontend.ui.SingleLineTextFieldKind
import org.koin.compose.viewmodel.koinViewModel

@Composable
internal fun ReactionPickerDialog(dialogUiState: ReactionPickerDialogUiState) {
    // 画面のストアに置くと、閉じた後も画面を離れるまで開いた回数だけ残るので、開いている間だけのストアに置く
    val viewModelStoreOwner = remember(dialogUiState.requestKey) {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(viewModelStoreOwner) {
        onDispose { viewModelStoreOwner.viewModelStore.clear() }
    }
    val viewModel = koinViewModel<ReactionPickerViewModel>(viewModelStoreOwner = viewModelStoreOwner)
    val uiState by viewModel.uiStateFlow.collectAsState()

    LaunchedEffect(viewModel.eventHandler, dialogUiState.listener) {
        viewModel.eventHandler.collect(
            object : ReactionPickerViewModel.Event {
                override fun pick(reaction: PickedReaction) {
                    dialogUiState.listener.onPick(reaction)
                }
            },
        )
    }

    Dialog(onDismissRequest = dialogUiState.listener::onDismiss) {
        ReactionPickerContent(
            uiState = uiState,
            removeButtonVisible = dialogUiState.removeButtonVisible,
            dialogListener = dialogUiState.listener,
        )
    }
}

@Composable
internal fun ReactionPickerContent(
    uiState: ReactionPickerUiState,
    removeButtonVisible: Boolean,
    dialogListener: ReactionPickerDialogUiState.Listener,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 480.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(top = 16.dp)) {
            Text(
                text = "リアクション",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            SingleLineTextField(
                value = uiState.searchQuery,
                onValueChange = uiState.listener::onChangeSearchQuery,
                onImeAction = {},
                label = "絵文字を検索",
                kind = SingleLineTextFieldKind.Plain,
                autofill = null,
                formId = null,
                required = false,
                imeAction = ImeAction.Search,
                inputId = "reaction-search",
                inputName = "reaction-search",
                enabled = true,
                hasError = false,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            PrimaryTabRow(selectedTabIndex = uiState.selectedTab.ordinal, containerColor = Color.Transparent) {
                ReactionPickerUiState.Tab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { uiState.listener.onSelectTab(tab) },
                        text = {
                            Text(
                                when (tab) {
                                    ReactionPickerUiState.Tab.CustomEmoji -> "カスタム絵文字"
                                    ReactionPickerUiState.Tab.Unicode -> "絵文字"
                                },
                            )
                        },
                    )
                }
            }
            // タブや検索で中身が変わってもダイアログの高さを変えない
            Box(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                PickerContent(content = uiState.content, listener = uiState.listener)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                if (removeButtonVisible) {
                    TextButton(onClick = dialogListener::onClickRemove) {
                        Text("リアクションを外す")
                    }
                }
                TextButton(onClick = dialogListener::onDismiss) {
                    Text("閉じる")
                }
            }
        }
    }
}

@Composable
private fun PickerContent(
    content: ReactionPickerUiState.Content,
    listener: ReactionPickerUiState.Listener,
) {
    when (content) {
        ReactionPickerUiState.Content.Loading -> {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is ReactionPickerUiState.Content.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = content.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = listener::onClickRetry) {
                    Text("再読み込み")
                }
            }
        }

        is ReactionPickerUiState.Content.CustomEmojis -> {
            val emptyMessage = content.emptyMessage
            if (emptyMessage != null) {
                EmptyMessage(emptyMessage)
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = EMOJI_CELL_SIZE), modifier = Modifier.padding(horizontal = 8.dp)) {
                    items(content.items, key = { it.shortcode }) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable(onClick = item.listener::onClick)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = item.imageUrl,
                                contentDescription = ":${item.shortcode}:",
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                }
            }
        }

        is ReactionPickerUiState.Content.UnicodeEmojis -> {
            if (content.emptyMessageVisible) {
                EmptyMessage("見つかりませんでした")
            } else {
                LazyVerticalGrid(columns = GridCells.Adaptive(minSize = EMOJI_CELL_SIZE), modifier = Modifier.padding(horizontal = 8.dp)) {
                    content.sections.forEach { section ->
                        val title = section.title
                        if (title != null) {
                            item(key = "title-$title", span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    text = title,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(section.items, key = { it.emoji }) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clickable(onClick = item.listener::onClick),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = item.emoji, fontSize = 26.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private val EMOJI_CELL_SIZE = 44.dp
