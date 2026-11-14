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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostMention
import net.matsudamper.kmp.activitypub.frontend.screen.PostDetailDialogFrame
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.screen.createTextWithEmojis
import net.matsudamper.kmp.activitypub.frontend.ui.DeleteNoteDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

@Preview
@Composable
private fun PostDetailContentPreview() = PostDetailLoadedPreview(deleteNoteDialog = null, mentionText = "")

@Preview
@Composable
private fun PostDetailContentMentionTypedPreview() = PostDetailLoadedPreview(deleteNoteDialog = null, mentionText = "@alice@example.social こんにちは")

@Preview
@Composable
private fun PostDetailContentDeleteNoteDialogPreview() = PostDetailLoadedPreview(
    deleteNoteDialog = DeleteNoteDialogUiState(deleteButtonEnabled = true, listener = AndroidPreviewDeleteNoteDialogListener),
    mentionText = "",
)

@Preview(heightDp = 1000)
@Composable
private fun PostDetailContentReplyTargetLoadingPreview() = PostDetailReplyTargetPreview(
    replyTargetLoader = PostDetailScreenUiState.ReplyTargetLoader.Loading(AndroidPreviewReplyTargetLoadingListener),
)

@Preview(heightDp = 1000)
@Composable
private fun PostDetailContentReplyTargetUnavailablePreview() = PostDetailReplyTargetPreview(
    replyTargetLoader = PostDetailScreenUiState.ReplyTargetLoader.Unavailable,
)

@Preview(heightDp = 1000)
@Composable
private fun PostDetailContentReplyTargetErrorPreview() = PostDetailReplyTargetPreview(
    replyTargetLoader = PostDetailScreenUiState.ReplyTargetLoader.Error(AndroidPreviewReplyTargetErrorListener),
)

@Composable
private fun PostDetailReplyTargetPreview(replyTargetLoader: PostDetailScreenUiState.ReplyTargetLoader) {
    MaterialTheme {
        PostDetailContent(
            uiState = createLoadedUiState(
                images = listOf(),
                deleteNoteDialog = null,
                mentionText = "",
                replyTargetLoader = replyTargetLoader,
                replyTargets = listOf(
                    previewReplyTarget(displayName = "Bob", acct = "@bob@remote.example", content = "最初の投稿です"),
                    previewReplyTarget(displayName = "Carol", acct = "@carol@other.example", content = "@bob@remote.example それに返信します"),
                ),
            ),
            snackbarHostState = SnackbarHostState(),
            layout = PostDetailLayout.FullScreen,
        )
    }
}

private fun previewReplyTarget(displayName: String, acct: String, content: String) = PostItemUiState(
    pinnedLabelVisible = false,
    deletedLabelVisible = false,
    actorLabel = null,
    iconUrl = null,
    displayName = createTextWithEmojis(text = displayName, emojis = listOf()),
    acct = acct,
    body = createPostBodyText(content = content, mentions = listOf(), emojis = listOf(), onOpenUrl = {}, onOpenProfile = {}),
    postedAt = "1時間",
    fullPostedAt = "2026/11/01 20:00",
    linkPreviews = listOf(),
    images = listOf(),
    favouriteCountText = null,
    boostCountText = null,
    favouriteButton = PostItemUiState.FavouriteButton(iconHighlighted = false, listener = AndroidPreviewFavouriteButtonListener),
    boostButton = PostItemUiState.BoostButton(iconHighlighted = false, listener = AndroidPreviewBoostButtonListener),
    stamps = listOf(),
    authorListener = null,
    postListener = null,
    shareListener = null,
    menuItems = listOf(),
    listener = AndroidPreviewPostItemListener,
)

@Composable
private fun PostDetailLoadedPreview(
    deleteNoteDialog: DeleteNoteDialogUiState?,
    mentionText: String,
) {
    MaterialTheme {
        PostDetailContent(
            uiState = createLoadedUiState(
                images = listOf(previewImage(index = 1), previewImage(index = 2)),
                deleteNoteDialog = deleteNoteDialog,
                mentionText = mentionText,
                replyTargetLoader = null,
                replyTargets = listOf(),
            ),
            snackbarHostState = SnackbarHostState(),
            layout = PostDetailLayout.FullScreen,
        )
    }
}

private fun createLoadedUiState(
    images: List<PostItemUiState.Image>,
    deleteNoteDialog: DeleteNoteDialogUiState?,
    mentionText: String,
    replyTargetLoader: PostDetailScreenUiState.ReplyTargetLoader?,
    replyTargets: List<PostItemUiState>,
): PostDetailScreenUiState {
    return PostDetailScreenUiState(
        content = PostDetailScreenUiState.Content.Loaded(
            replyTargetLoader = replyTargetLoader,
            replyTargets = replyTargets,
            post = PostItemUiState(
                pinnedLabelVisible = false,
                deletedLabelVisible = false,
                actorLabel = null,
                iconUrl = null,
                displayName = createTextWithEmojis(text = "Alice", emojis = listOf()),
                acct = "@alice@example.social",
                body = createPostBodyText(
                    content = "@alice 個別の投稿の画面です。 https://kotlinlang.org/",
                    mentions = listOf(UserPostMention(start = 0, end = 6, acct = "alice@example.social")),
                    emojis = listOf(),
                    onOpenUrl = {},
                    onOpenProfile = {},
                ),
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
            mentionBox = PostDetailScreenUiState.MentionBox(
                text = mentionText,
                placeholder = "@alice@example.social へメンション",
                textFieldEnabled = true,
                sendButtonEnabled = mentionText.isNotBlank(),
                listener = AndroidPreviewMentionBoxListener,
            ),
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
                        uiState = createLoadedUiState(
                            images = (1..imageCount).map { previewImage(index = it) },
                            deleteNoteDialog = null,
                            mentionText = "",
                            replyTargetLoader = null,
                            replyTargets = listOf(),
                        ),
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

private object AndroidPreviewReplyTargetLoadingListener : PostDetailScreenUiState.ReplyTargetLoader.Loading.Listener {
    override fun onVisible() = Unit
}

private object AndroidPreviewReplyTargetErrorListener : PostDetailScreenUiState.ReplyTargetLoader.Error.Listener {
    override fun onClickRetry() = Unit
}

private object AndroidPreviewMentionBoxListener : PostDetailScreenUiState.MentionBox.Listener {
    override fun onTextChange(text: String) = Unit

    override fun onClickSend() = Unit
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
