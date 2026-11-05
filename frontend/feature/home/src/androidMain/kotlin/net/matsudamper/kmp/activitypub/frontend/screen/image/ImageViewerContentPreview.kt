package net.matsudamper.kmp.activitypub.frontend.screen.image

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler

@OptIn(ExperimentalCoilApi::class)
@Preview
@Composable
private fun ImageViewerContentPreview() {
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler {
            ColorImage(color = 0xFF26A69A.toInt(), width = 400, height = 300)
        },
    ) {
        MaterialTheme {
            Box(modifier = Modifier.size(width = 360.dp, height = 640.dp)) {
                ImageViewerContent(
                    uiState = ImageViewerScreenUiState(
                        imageUrl = "https://example.com/image.png",
                        listener = AndroidPreviewImageViewerListener,
                    ),
                )
            }
        }
    }
}

private object AndroidPreviewImageViewerListener : ImageViewerScreenUiState.Listener {
    override fun onClickClose() = Unit
}
