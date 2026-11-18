package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.ImageRequest
import net.matsudamper.kmp.activitypub.frontend.screen.PostDialogFrame
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.screen.createTextWithEmojis
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState

@Preview
@Composable
private fun PostContentPreview() {
    PostPreviewTheme {
        PostContent(
            navigationIcon = PostNavigationIcon.Back,
            uiState = previewUiState(images = listOf(), addImageLoadingVisible = false, quote = null, replyTarget = null, contentWarning = null),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview(widthDp = 1200, heightDp = 800)
@Composable
private fun PostDialogMentionPreview() {
    PostPreviewTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
        ) {
            PostDialogFrame {
                PostContent(
                    navigationIcon = PostNavigationIcon.Close,
                    uiState = previewUiState(images = listOf(), addImageLoadingVisible = false, quote = null, replyTarget = null, contentWarning = null)
                        .copy(body = "@alice@example.social "),
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun PostContentQuotePreview() {
    PostPreviewTheme {
        PostContent(
            navigationIcon = PostNavigationIcon.Back,
            uiState = previewUiState(
                images = listOf(),
                addImageLoadingVisible = false,
                quote = PostItemUiState.Quote.Loaded(
                    iconUrl = null,
                    displayName = createTextWithEmojis(text = "Bob", emojis = listOf()),
                    acct = "@bob@remote.example",
                    body = createPostBodyText(content = "引用する投稿です", mentions = listOf(), emojis = listOf(), onOpenUrl = {}, onOpenProfile = {}),
                    listener = null,
                ),
                replyTarget = null,
                contentWarning = null,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun PostContentReplyPreview() {
    PostPreviewTheme {
        PostContent(
            navigationIcon = PostNavigationIcon.Close,
            uiState = previewUiState(
                images = listOf(),
                addImageLoadingVisible = false,
                quote = null,
                replyTarget = PostScreenUiState.ReplyTarget(
                    post = PostItemUiState.Quote.Loaded(
                        iconUrl = null,
                        displayName = createTextWithEmojis(text = "Bob", emojis = listOf()),
                        acct = "@bob@remote.example",
                        body = createPostBodyText(content = "返信する投稿です", mentions = listOf(), emojis = listOf(), onOpenUrl = {}, onOpenProfile = {}),
                        listener = null,
                    ),
                    removeButtonEnabled = true,
                    listener = AndroidPreviewReplyTargetListener,
                ),
                contentWarning = null,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun PostContentContentWarningPreview() {
    PostPreviewTheme {
        PostContent(
            navigationIcon = PostNavigationIcon.Back,
            uiState = previewUiState(images = listOf(), addImageLoadingVisible = false, quote = null, replyTarget = null, contentWarning = "ネタバレ"),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

/** 投稿カラムを最小幅まで縮めても、投稿ボタンが潰れないことを確かめる */
@Preview(widthDp = 240)
@Composable
private fun PostContentNarrowWidthPreview() {
    PostPreviewTheme {
        PostContent(
            navigationIcon = PostNavigationIcon.Close,
            uiState = previewUiState(images = listOf(), addImageLoadingVisible = false, quote = null, replyTarget = null, contentWarning = null),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

/** ソフトウェアキーボードで高さが詰まったときに、投稿ボタンまでスクロールで届くことを確かめる */
@Preview(heightDp = 240)
@Composable
private fun PostContentShortHeightPreview() {
    PostPreviewTheme {
        PostContent(
            navigationIcon = PostNavigationIcon.Back,
            uiState = previewUiState(images = listOf(), addImageLoadingVisible = false, quote = null, replyTarget = null, contentWarning = null),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun PostContentImagesPreview() {
    PostPreviewTheme {
        PostContent(
            navigationIcon = PostNavigationIcon.Back,
            uiState = previewUiState(
                images = List(2) {
                    PostScreenUiState.Image(
                        preview = PostScreenUiState.ImagePreview { context -> ImageRequest.Builder(context).data(ByteArray(0)).build() },
                        removeButtonEnabled = true,
                        listener = AndroidPreviewImageListener,
                    )
                } + PostScreenUiState.Image(
                    preview = PostScreenUiState.VideoPreview(videoUrl = "https://example.com/video.mp4"),
                    removeButtonEnabled = true,
                    listener = AndroidPreviewImageListener,
                ),
                addImageLoadingVisible = true,
                quote = null,
                replyTarget = null,
                contentWarning = null,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun PostContentEditInColumnPreview() {
    PostPreviewTheme {
        PostContent(
            navigationIcon = PostNavigationIcon.Close,
            uiState = previewUiState(images = listOf(), addImageLoadingVisible = false, quote = null, replyTarget = null, contentWarning = null).copy(
                title = "投稿を編集",
                contentWarningButtonVisible = false,
                postButtonText = "更新",
                editTarget = PostScreenUiState.EditTarget(cancelButtonEnabled = true, listener = AndroidPreviewEditTargetListener),
                addImageButtonEnabled = false,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun PostContentEditLoadingPreview() {
    PostPreviewTheme {
        PostContent(
            navigationIcon = PostNavigationIcon.Close,
            uiState = previewUiState(images = listOf(), addImageLoadingVisible = false, quote = null, replyTarget = null, contentWarning = null).copy(
                title = "投稿を編集",
                contentWarningButtonVisible = false,
                body = "",
                bodyLoadingVisible = true,
                inputEnabled = false,
                postButtonEnabled = false,
                postButtonText = "更新",
                addImageButtonEnabled = false,
                addEmojiButtonEnabled = false,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun DiscardDraftDialogPreview() {
    PostPreviewTheme {
        DiscardDraftDialog(
            dialog = PostScreenUiState.DiscardDraftDialog(
                message = "書きかけの投稿を破棄して、投稿の編集を始めますか",
                discardButtonText = "破棄して編集",
                listener = AndroidPreviewDiscardDraftDialogListener,
            ),
        )
    }
}

/**
 * @param contentWarning 閲覧注意にしないなら null
 */
private fun previewUiState(
    images: List<PostScreenUiState.Image>,
    addImageLoadingVisible: Boolean,
    quote: PostItemUiState.Quote?,
    replyTarget: PostScreenUiState.ReplyTarget?,
    contentWarning: String?,
): PostScreenUiState =
    PostScreenUiState(
        title = "投稿",
        body = "Kotlin 2.4 がリリースされました",
        bodyInsertion = null,
        contentWarningInputVisible = contentWarning != null,
        contentWarning = contentWarning.orEmpty(),
        contentWarningButtonVisible = true,
        contentWarningButtonChecked = contentWarning != null,
        contentWarningButtonEnabled = true,
        bodyLoadingVisible = false,
        inputHasError = false,
        inputEnabled = true,
        closeButtonEnabled = true,
        postButtonEnabled = true,
        postButtonText = "投稿",
        images = images,
        quote = quote,
        replyTarget = replyTarget,
        editTarget = null,
        discardDraftDialog = null,
        addImageButtonEnabled = addImageLoadingVisible.not(),
        addImageLoadingVisible = addImageLoadingVisible,
        addEmojiButtonEnabled = true,
        sensitiveChipSelected = images.isNotEmpty(),
        sensitiveChipEnabled = true,
        emojiPickerDialog = null,
        listener = AndroidPreviewPostListener,
    )

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun PostPreviewTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { ColorImage(0xFF4FC3F7.toInt()) },
    ) {
        MaterialTheme(content = content)
    }
}

private object AndroidPreviewReplyTargetListener : PostScreenUiState.ReplyTargetListener {
    override fun onClickRemove() = Unit
}

private object AndroidPreviewPostListener : PostScreenUiState.Listener {
    override fun onBodyChanged(text: String) = Unit

    override fun onBodyInsertionHandled() = Unit

    override fun onClickContentWarning() = Unit

    override fun onContentWarningChanged(text: String) = Unit

    override fun onClickAddImage() = Unit

    override fun onImagePicked(file: PickedImageFile) = Unit

    override fun onClickAddEmoji() = Unit

    override fun onClickSensitive() = Unit

    override fun onReplyRequested(postId: String) = Unit

    override fun onEditRequested(noteId: String) = Unit

    override fun onClickPost() = Unit

    override fun onPressPostShortcut() = Unit

    override fun onClickClose() = Unit
}

private object AndroidPreviewEditTargetListener : PostScreenUiState.EditTargetListener {
    override fun onClickCancel() = Unit
}

private object AndroidPreviewDiscardDraftDialogListener : PostScreenUiState.DiscardDraftDialogListener {
    override fun onClickDiscard() = Unit

    override fun onDismiss() = Unit
}

private object AndroidPreviewImageListener : PostScreenUiState.ImageListener {
    override fun onClickRemove() = Unit
}
