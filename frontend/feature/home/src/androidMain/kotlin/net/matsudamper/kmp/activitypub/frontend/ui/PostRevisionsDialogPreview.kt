package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText

@Preview
@Composable
private fun PostRevisionsDialogPreview() {
    MaterialTheme {
        PostRevisionsDialog(
            uiState = PostRevisionsDialogUiState(
                content = PostRevisionsDialogUiState.Content.Loaded(
                    positionText = "2 / 3",
                    revisedAt = "2026/11/01 21:05",
                    body = createPostBodyText(content = "誤字を直した 2 番目の版です", mentions = listOf(), emojis = listOf(), onOpenUrl = {}, onOpenProfile = {}),
                    images = listOf(),
                    olderButtonEnabled = true,
                    newerButtonEnabled = true,
                ),
                listener = AndroidPreviewPostRevisionsListener,
            ),
        )
    }
}

@Preview
@Composable
private fun PostRevisionsDialogLoadingPreview() {
    MaterialTheme {
        PostRevisionsDialog(
            uiState = PostRevisionsDialogUiState(content = PostRevisionsDialogUiState.Content.Loading, listener = AndroidPreviewPostRevisionsListener),
        )
    }
}

@Preview
@Composable
private fun PostRevisionsDialogErrorPreview() {
    MaterialTheme {
        PostRevisionsDialog(
            uiState = PostRevisionsDialogUiState(content = PostRevisionsDialogUiState.Content.Error, listener = AndroidPreviewPostRevisionsListener),
        )
    }
}

private object AndroidPreviewPostRevisionsListener : PostRevisionsDialogUiState.Listener {
    override fun onClickOlder() = Unit

    override fun onClickNewer() = Unit

    override fun onClickRetry() = Unit

    override fun onDismiss() = Unit
}
