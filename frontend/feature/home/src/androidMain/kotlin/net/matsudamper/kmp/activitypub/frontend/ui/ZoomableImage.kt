package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

@Composable
internal actual fun ZoomableImage(
    imageUrl: String,
    modifier: Modifier,
) {
    val zoomState = rememberZoomState()
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        onSuccess = { state -> zoomState.setContentSize(state.painter.intrinsicSize) },
        modifier = modifier.zoomable(zoomState),
    )
}
