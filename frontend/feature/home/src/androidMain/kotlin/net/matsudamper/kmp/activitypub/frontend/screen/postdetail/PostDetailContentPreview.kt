package net.matsudamper.kmp.activitypub.frontend.screen.postdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import net.matsudamper.kmp.activitypub.frontend.screen.PostDetailDialogFrame
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
            uiState = createLoadedUiState(images = listOf(previewImage(index = 1), previewImage(index = 2)), deleteNoteDialog = deleteNoteDialog),
            snackbarHostState = SnackbarHostState(),
            layout = PostDetailLayout.FullScreen,
        )
    }
}

private fun createLoadedUiState(
    images: List<PostItemUiState.Image>,
    deleteNoteDialog: DeleteNoteDialogUiState?,
): PostDetailScreenUiState {
    return PostDetailScreenUiState(
        content = PostDetailScreenUiState.Content.Loaded(
            post = PostItemUiState(
                pinnedLabelVisible = false,
                boostedBy = null,
                iconUrl = null,
                displayName = createTextWithEmojis(text = "Alice", emojis = listOf()),
                acct = "@alice@example.social",
                body = createPostBodyText(content = "個別の投稿の画面です。 https://kotlinlang.org/", emojis = listOf(), onOpenUrl = {}),
                postedAt = "2026-11-01T12:00:00Z",
                fullPostedAt = "2026/11/01 21:00",
                linkPreviews = listOf(
                    PostItemUiState.LinkPreview(
                        title = "Kotlin Programming Language",
                        siteName = "Kotlin",
                        imageUrl = null,
                        faviconUrl = null,
                        listener = AndroidPreviewLinkPreviewListener,
                    ),
                ),
                images = images,
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
            mentionBoxPlaceholder = "@alice@example.social へメンション",
        ),
        deleteNoteDialog = deleteNoteDialog,
        listener = AndroidPreviewPostDetailListener,
    )
}

@Preview(widthDp = 1200, heightDp = 900)
@Composable
private fun PostDetailDialogPreview() = PostDetailDialogPreviewContent(imageCount = 6, colorScheme = lightColorScheme())

@Preview(widthDp = 1200, heightDp = 900)
@Composable
private fun PostDetailDialogNoImagePreview() = PostDetailDialogPreviewContent(imageCount = 0, colorScheme = lightColorScheme())

@Preview(widthDp = 1200, heightDp = 900)
@Composable
private fun PostDetailDialogDarkPreview() = PostDetailDialogPreviewContent(imageCount = 0, colorScheme = darkColorScheme())

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun PostDetailDialogPreviewContent(
    imageCount: Int,
    colorScheme: ColorScheme,
) {
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { ColorImage(0xFF5C6BC0.toInt()) },
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center,
            ) {
                PostDetailDialogFrame(onDismissRequest = {}) {
                    PostDetailContent(
                        uiState = createLoadedUiState(images = (1..imageCount).map { previewImage(index = it) }, deleteNoteDialog = null),
                        snackbarHostState = SnackbarHostState(),
                        layout = PostDetailLayout.Dialog,
                    )
                }
            }
        }
    }
}

private fun previewImage(index: Int) = PostItemUiState.Image(
    url = "https://example.social/image$index.png",
    description = null,
    blurToggleButtonVisible = false,
    blurred = false,
    listener = AndroidPreviewImageListener,
)

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
            layout = PostDetailLayout.FullScreen,
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
