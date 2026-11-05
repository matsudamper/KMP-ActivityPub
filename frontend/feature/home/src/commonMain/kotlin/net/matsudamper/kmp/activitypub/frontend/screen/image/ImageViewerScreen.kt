package net.matsudamper.kmp.activitypub.frontend.screen.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.ui.ComposeCanDrawOverZoomableImage
import net.matsudamper.kmp.activitypub.frontend.ui.ZoomableImage
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

@Composable
internal fun ImageViewerScreen(
    imageUrl: String,
    navigator: Navigator,
) {
    val uiState = remember(imageUrl, navigator) {
        ImageViewerScreenUiState(
            imageUrl = imageUrl,
            listener = object : ImageViewerScreenUiState.Listener {
                override fun onClickClose() {
                    navigator.back()
                }

                override fun onClickOutsideImage() {
                    navigator.back()
                }
            },
        )
    }
    ImageViewerContent(uiState = uiState)
}

@Composable
internal fun ImageViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    if (ComposeCanDrawOverZoomableImage) {
        OverlayImageViewerContent(uiState = uiState)
    } else {
        ImageOnlyViewerContent(uiState = uiState)
    }
}

@Composable
private fun OverlayImageViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    var imageZoomed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
    ) {
        ZoomableImage(
            imageUrl = uiState.imageUrl,
            onClickOutsideImage = uiState.listener::onClickOutsideImage,
            onZoomedChange = { imageZoomed = it },
            modifier = Modifier.fillMaxSize(),
        )
        if (!imageZoomed) {
            CloseButton(
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(8.dp),
            )
        }
    }
}

/**
 * 閉じるボタンを画像に重ねられないので出さず、画像の外を押して閉じる
 */
@Composable
private fun ImageOnlyViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    ZoomableImage(
        imageUrl = uiState.imageUrl,
        onClickOutsideImage = uiState.listener::onClickOutsideImage,
        onZoomedChange = {},
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
    )
}

@Composable
private fun CloseButton(
    uiState: ImageViewerScreenUiState,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = uiState.listener::onClickClose,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White,
        ),
        modifier = modifier,
    ) {
        Icon(
            imageVector = AppIcons.Close,
            contentDescription = "閉じる",
        )
    }
}
