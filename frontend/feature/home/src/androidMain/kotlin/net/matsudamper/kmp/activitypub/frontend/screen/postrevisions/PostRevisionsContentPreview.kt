package net.matsudamper.kmp.activitypub.frontend.screen.postrevisions

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

@Preview
@Composable
private fun PostRevisionsContentPreview() {
    MaterialTheme {
        PostRevisionsContent(
            uiState = PostRevisionsScreenUiState(
                content = PostRevisionsScreenUiState.Content.Loaded(
                    revisions = listOf(
                        previewRevision(listKey = "current", revisedAt = "2026/11/01 21:10", current = true, content = "誤字を直して画像を足した今の版です", imageCount = 1),
                        previewRevision(listKey = "2", revisedAt = "2026/11/01 21:05", current = false, content = "誤字を直した 2 番目の版です", imageCount = 0),
                        previewRevision(listKey = "1", revisedAt = "2026/11/01 21:00", current = false, content = "誤字のある最初の版です", imageCount = 0),
                    ),
                    footer = PostRevisionsScreenUiState.Footer.Loading,
                ),
                listener = AndroidPreviewPostRevisionsListener,
            ),
        )
    }
}

@Preview
@Composable
private fun PostRevisionsContentLoadingPreview() {
    MaterialTheme {
        PostRevisionsContent(
            uiState = PostRevisionsScreenUiState(
                content = PostRevisionsScreenUiState.Content.Loading,
                listener = AndroidPreviewPostRevisionsListener,
            ),
        )
    }
}

@Preview
@Composable
private fun PostRevisionsContentErrorPreview() {
    MaterialTheme {
        PostRevisionsContent(
            uiState = PostRevisionsScreenUiState(
                content = PostRevisionsScreenUiState.Content.Error(message = "ネットワークエラー"),
                listener = AndroidPreviewPostRevisionsListener,
            ),
        )
    }
}

private fun previewRevision(listKey: String, revisedAt: String, current: Boolean, content: String, imageCount: Int) = PostRevisionsScreenUiState.Revision(
    listKey = listKey,
    revisedAt = revisedAt,
    currentLabelVisible = current,
    body = createPostBodyText(content = content, mentions = listOf(), emojis = listOf(), onOpenUrl = {}, onOpenProfile = {}),
    images = List(imageCount) { index ->
        PostItemUiState.Image(
            url = "https://example.social/images/$index.png",
            description = null,
            blurToggleButtonVisible = false,
            blurred = false,
            listener = AndroidPreviewImageListener,
        )
    },
)

private object AndroidPreviewPostRevisionsListener : PostRevisionsScreenUiState.Listener {
    override fun onClickBack() = Unit

    override fun onClickRetry() = Unit

    override fun onScrolledToEnd() = Unit

    override fun onClickRetryLoadMore() = Unit
}

private object AndroidPreviewImageListener : PostItemUiState.ImageListener {
    override fun onClick() = Unit

    override fun onClickBlurToggle() = Unit
}
