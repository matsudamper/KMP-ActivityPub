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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
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
            },
        )
    }
    ImageViewerContent(uiState = uiState)
}

@Composable
internal fun ImageViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
    ) {
        val zoomState = rememberZoomState()
        AsyncImage(
            model = uiState.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onSuccess = { state -> zoomState.setContentSize(state.painter.intrinsicSize) },
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoomState),
        )
        IconButton(
            onClick = uiState.listener::onClickClose,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .safeDrawingPadding()
                .padding(8.dp),
        ) {
            Icon(
                imageVector = AppIcons.Close,
                contentDescription = "閉じる",
            )
        }
    }
}
