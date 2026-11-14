package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun ReactionChangeDialogPreview() {
    MaterialTheme {
        ReactionConfirmDialog(
            uiState = ReactionConfirmDialogUiState(
                title = "リアクションを変更",
                confirmButtonText = "変える",
                current = ReactionConfirmDialogUiState.Emoji(emoji = "👍", imageUrl = null),
                next = ReactionConfirmDialogUiState.Emoji(emoji = "🎉", imageUrl = null),
                listener = AndroidPreviewListener,
            ),
        )
    }
}

@Preview
@Composable
private fun ReactionRemoveDialogPreview() {
    MaterialTheme {
        ReactionConfirmDialog(
            uiState = ReactionConfirmDialogUiState(
                title = "リアクションを外す",
                confirmButtonText = "外す",
                current = ReactionConfirmDialogUiState.Emoji(emoji = "👍", imageUrl = null),
                next = null,
                listener = AndroidPreviewListener,
            ),
        )
    }
}

private object AndroidPreviewListener : ReactionConfirmDialogUiState.Listener {
    override fun onConfirm() = Unit

    override fun onDismiss() = Unit
}
