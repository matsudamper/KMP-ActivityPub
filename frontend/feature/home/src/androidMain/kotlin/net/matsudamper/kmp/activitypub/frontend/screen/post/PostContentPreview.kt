package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.ImageRequest
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile

@Preview
@Composable
private fun PostContentPreview() {
    PostPreviewTheme {
        PostContent(
            uiState = previewUiState(images = listOf(), addImageLoadingVisible = false),
            navigationIcon = PostNavigationIcon.Back,
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun PostContentImagesPreview() {
    PostPreviewTheme {
        PostContent(
            uiState = previewUiState(
                images = List(3) {
                    PostScreenUiState.Image(
                        preview = { context -> ImageRequest.Builder(context).data(ByteArray(0)).build() },
                        removeButtonEnabled = true,
                        listener = AndroidPreviewImageListener,
                    )
                },
                addImageLoadingVisible = true,
            ),
            navigationIcon = PostNavigationIcon.Back,
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private fun previewUiState(
    images: List<PostScreenUiState.Image>,
    addImageLoadingVisible: Boolean,
): PostScreenUiState =
    PostScreenUiState(
        body = "Kotlin 2.4 がリリースされました",
        inputHasError = false,
        inputEnabled = true,
        backButtonEnabled = true,
        postButtonEnabled = true,
        images = images,
        addImageButtonEnabled = addImageLoadingVisible.not(),
        addImageLoadingVisible = addImageLoadingVisible,
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

private object AndroidPreviewPostListener : PostScreenUiState.Listener {
    override fun onBodyChanged(text: String) = Unit

    override fun onClickAddImage() = Unit

    override fun onImagePicked(file: PickedImageFile) = Unit

    override fun onClickPost() = Unit

    override fun onPressPostShortcut() = Unit

    override fun onClickBack() = Unit
}

private object AndroidPreviewImageListener : PostScreenUiState.ImageListener {
    override fun onClickRemove() = Unit
}
