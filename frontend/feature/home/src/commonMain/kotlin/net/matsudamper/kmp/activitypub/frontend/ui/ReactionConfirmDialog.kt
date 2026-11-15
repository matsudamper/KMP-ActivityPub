package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

@Composable
internal fun ReactionConfirmDialog(uiState: ReactionConfirmDialogUiState) {
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text(uiState.title) },
        text = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DialogEmoji(uiState.current)
                val next = uiState.next
                if (next != null) {
                    Icon(
                        imageVector = AppIcons.ArrowForward,
                        contentDescription = "から",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (next) {
                        is ReactionConfirmDialogUiState.Next.ToEmoji -> DialogEmoji(next.emoji)

                        ReactionConfirmDialogUiState.Next.ToLike -> Icon(
                            imageVector = AppIcons.Star,
                            contentDescription = "お気に入り",
                            modifier = Modifier.size(40.dp),
                            tint = FavouriteHighlightedColor,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = uiState.listener::onConfirm) {
                Text(uiState.confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = uiState.listener::onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

/**
 * カスタム絵文字の画像が取れなければ :name: の文字で出す
 */
@Composable
private fun DialogEmoji(emoji: ReactionConfirmDialogUiState.Emoji) {
    val imageUrl = emoji.imageUrl
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    if (imageUrl == null || imageFailed) {
        Text(text = emoji.emoji, fontSize = 32.sp)
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = emoji.emoji,
            modifier = Modifier.size(40.dp),
            onError = { imageFailed = true },
        )
    }
}
