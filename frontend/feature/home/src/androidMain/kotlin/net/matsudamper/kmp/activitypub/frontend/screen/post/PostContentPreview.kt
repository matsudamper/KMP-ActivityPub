package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.ui.PickedImageFile

@Preview
@Composable
private fun PostContentPreview() {
    MaterialTheme {
        PostContent(
            uiState = previewUiState(images = listOf(), addImageLoadingVisible = false),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun PostContentImagesPreview() {
    MaterialTheme {
        PostContent(
            uiState = previewUiState(
                images = listOf(Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFF81C784)).map { color ->
                    PostScreenUiState.Image(
                        bitmap = previewBitmap(color),
                        removeButtonEnabled = true,
                        listener = AndroidPreviewImageListener,
                    )
                },
                addImageLoadingVisible = true,
            ),
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

private fun previewBitmap(color: Color): ImageBitmap {
    val bitmap = ImageBitmap(width = 120, height = 80)
    Canvas(bitmap).apply {
        drawRect(Rect(0f, 0f, 120f, 80f), Paint().apply { this.color = color })
        drawRect(Rect(20f, 20f, 60f, 60f), Paint().apply { this.color = Color.White })
    }
    return bitmap
}

private object AndroidPreviewPostListener : PostScreenUiState.Listener {
    override fun onBodyChanged(text: String) = Unit

    override fun onClickAddImage() = Unit

    override fun onImagePicked(file: PickedImageFile) = Unit

    override fun onClickPost() = Unit

    override fun onClickBack() = Unit
}

private object AndroidPreviewImageListener : PostScreenUiState.ImageListener {
    override fun onClickRemove() = Unit
}
