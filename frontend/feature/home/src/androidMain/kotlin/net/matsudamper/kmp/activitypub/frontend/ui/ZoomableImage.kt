package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import kotlin.math.abs
import kotlin.math.min
import net.engawapg.lib.zoomable.ZoomState
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable

internal actual val ComposeCanDrawOverZoomableImage: Boolean = true

@Composable
internal actual fun ZoomableImage(
    imageUrl: String,
    onClickOutsideImage: () -> Unit,
    onZoomedChange: (zoomed: Boolean) -> Unit,
    modifier: Modifier,
) {
    val zoomState = rememberZoomState()
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember(imageUrl) { mutableStateOf(Size.Unspecified) }
    val zoomed by remember(zoomState) { derivedStateOf { zoomState.scale > 1f } }
    val currentOnZoomedChange by rememberUpdatedState(onZoomedChange)
    LaunchedEffect(zoomed) {
        currentOnZoomedChange(zoomed)
    }
    AsyncImage(
        model = imageUrl,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        onSuccess = { state ->
            imageSize = state.painter.intrinsicSize
            zoomState.setContentSize(state.painter.intrinsicSize)
        },
        modifier = modifier
            .onSizeChanged { layoutSize = it }
            .zoomable(
                zoomState = zoomState,
                onTap = { position ->
                    val onImage = isOnDisplayedImage(
                        position = position,
                        layoutSize = layoutSize,
                        imageSize = imageSize,
                        zoomState = zoomState,
                    )
                    if (!onImage) {
                        onClickOutsideImage()
                    }
                },
            ),
    )
}

/**
 * zoomable は中心を起点に拡大し、その後に offset だけ動かして描く
 */
private fun isOnDisplayedImage(
    position: Offset,
    layoutSize: IntSize,
    imageSize: Size,
    zoomState: ZoomState,
): Boolean {
    if (imageSize == Size.Unspecified || imageSize.isEmpty() || layoutSize == IntSize.Zero) return false
    val fitScale = min(layoutSize.width / imageSize.width, layoutSize.height / imageSize.height)
    val center = Offset(layoutSize.width / 2f, layoutSize.height / 2f)
    val positionBeforeZoom = center + (position - center - Offset(zoomState.offsetX, zoomState.offsetY)) / zoomState.scale
    return abs(positionBeforeZoom.x - center.x) <= imageSize.width * fitScale / 2 &&
        abs(positionBeforeZoom.y - center.y) <= imageSize.height * fitScale / 2
}
