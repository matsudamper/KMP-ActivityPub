package net.matsudamper.kmp.activitypub.frontend.screen.postdetail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.screen.createTextWithEmojis
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

@Preview
@Composable
private fun PostDetailContentPreview() = PostDetailLoadedPreview(deleteNoteDialog = null)

@Preview
@Composable
private fun PostDetailContentDeleteNoteDialogPreview() = PostDetailLoadedPreview(
    deleteNoteDialog = DeleteNoteDialogUiState(deleteButtonEnabled = true, listener = AndroidPreviewDeleteNoteDialogListener),
)

@Composable
private fun PostDetailLoadedPreview(deleteNoteDialog: DeleteNoteDialogUiState?) {
    MaterialTheme {
        PostDetailContent(
            uiState = PostDetailScreenUiState(
                content = PostDetailScreenUiState.Content.Loaded(
                    post = PostItemUiState(
                        boostedBy = null,
                        iconUrl = null,
                        displayName = createTextWithEmojis(text = "Alice", emojis = listOf()),
                        acct = "@alice@example.social",
                        body = createPostBodyText(content = "個別の投稿の画面です。 https://kotlinlang.org/", emojis = listOf(), onOpenUrl = {}),
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
                        images = listOf(
                            PostItemUiState.Image(
                                url = "https://example.social/image1.png",
                                description = null,
                                blurToggleButtonVisible = false,
                                blurred = false,
                                listener = AndroidPreviewImageListener,
                            ),
                            PostItemUiState.Image(
                                url = "https://example.social/image2.png",
                                description = null,
                                blurToggleButtonVisible = false,
                                blurred = false,
                                listener = AndroidPreviewImageListener,
                            ),
                        ),
                        favouriteCountText = "12",
                        boostCountText = "4",
                        favouriteButton = PostItemUiState.FavouriteButton(iconHighlighted = false, listener = AndroidPreviewFavouriteButtonListener),
                        boostButton = PostItemUiState.BoostButton(iconHighlighted = true, listener = AndroidPreviewBoostButtonListener),
                        stamps = listOf(PostItemUiState.Stamp(emoji = "❤️", imageUrl = null, countText = "4")),
                        authorListener = null,
                        postListener = null,
                        shareListener = null,
                        menuItems = listOf(PostItemUiState.MenuItem(label = "削除", listener = AndroidPreviewMenuItemListener)),
                        listener = AndroidPreviewPostItemListener,
                    ),
                ),
                deleteNoteDialog = deleteNoteDialog,
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
                deleteNoteDialog = null,
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

private object AndroidPreviewMenuItemListener : PostItemUiState.MenuItemListener {
    override fun onClick() = Unit
}

private object AndroidPreviewDeleteNoteDialogListener : DeleteNoteDialogUiState.Listener {
    override fun onClickDelete() = Unit

    override fun onDismiss() = Unit
}

private object AndroidPreviewImageListener : PostItemUiState.ImageListener {
    override fun onClick() = Unit

    override fun onClickBlurToggle() = Unit
}

private object AndroidPreviewFavouriteButtonListener : PostItemUiState.FavouriteButtonListener {
    override fun onClick() = Unit
}

private object AndroidPreviewBoostButtonListener : PostItemUiState.BoostButtonListener {
    override fun onClick() = Unit
}
