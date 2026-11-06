package net.matsudamper.kmp.activitypub.frontend.screen.postdetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

@Preview
@Composable
private fun PostDetailContentPreview() {
    MaterialTheme {
        PostDetailContent(
            uiState = PostDetailScreenUiState(
                content = PostDetailScreenUiState.Content.Loaded(
                    post = PostItemUiState(
                        iconUrl = null,
                        displayName = "Alice",
                        acct = "@alice@example.social",
                        body = createPostBodyText(content = "個別の投稿の画面です。 https://kotlinlang.org/", onOpenUrl = {}),
                        postedAt = "2026-11-01T12:00:00Z",
                        linkPreviews = listOf(
                            PostItemUiState.LinkPreview(
                                title = "Kotlin Programming Language",
                                siteName = "Kotlin",
                                imageUrl = null,
                                faviconUrl = null,
                                listener = AndroidPreviewLinkPreviewListener,
                            ),
                        ),
                        images = listOf(),
                        favouriteCountText = "12",
                        favouriteButton = null,
                        stamps = listOf(PostItemUiState.Stamp(emoji = "❤️", imageUrl = null, countText = "4")),
                        authorListener = null,
                        postListener = null,
                        listener = AndroidPreviewPostItemListener,
                    ),
                ),
                listener = AndroidPreviewPostDetailListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun PostDetailContentNotFoundPreview() {
    MaterialTheme {
        PostDetailContent(
            uiState = PostDetailScreenUiState(
                content = PostDetailScreenUiState.Content.NotFound,
                listener = AndroidPreviewPostDetailListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private object AndroidPreviewPostDetailListener : PostDetailScreenUiState.Listener {
    override fun onClickRetry() = Unit

    override fun onClickBack() = Unit
}

private object AndroidPreviewPostItemListener : PostItemUiState.Listener {
    override fun onVisible() = Unit
}

private object AndroidPreviewLinkPreviewListener : PostItemUiState.LinkPreviewListener {
    override fun onClick() = Unit
}
