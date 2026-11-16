package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun PostRevisionsDialog(uiState: PostRevisionsDialogUiState) {
    val loaded = uiState.content as? PostRevisionsDialogUiState.Content.Loaded
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text("編集履歴") },
        text = {
            // 読み込みの前後と版の送り戻しでダイアログの高さが変わると、押しているボタンが動く
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(min = RevisionMinHeight, max = RevisionMaxHeight),
            ) {
                when (val content = uiState.content) {
                    PostRevisionsDialogUiState.Content.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                    PostRevisionsDialogUiState.Content.Error -> Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("編集履歴を読み込めませんでした")
                        TextButton(onClick = uiState.listener::onClickRetry) { Text("再読み込み") }
                    }

                    is PostRevisionsDialogUiState.Content.Loaded -> Revision(content = content)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = uiState.listener::onClickOlder,
                    enabled = loaded?.olderButtonEnabled == true,
                ) {
                    Text("前の版")
                }
                TextButton(
                    onClick = uiState.listener::onClickNewer,
                    enabled = loaded?.newerButtonEnabled == true,
                ) {
                    Text("次の版")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = uiState.listener::onDismiss) {
                Text("閉じる")
            }
        },
    )
}

@Composable
private fun Revision(content: PostRevisionsDialogUiState.Content.Loaded) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = content.revisedAt,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = content.positionText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EmojiText(
            text = content.body,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.Unspecified,
            fontWeight = null,
            maxLines = Int.MAX_VALUE,
            overflow = TextOverflow.Clip,
        )
        if (content.images.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(content.images) { image ->
                    PostImageCard(image = image)
                }
            }
        }
    }
}

private val RevisionMinHeight = 160.dp
private val RevisionMaxHeight = 420.dp
