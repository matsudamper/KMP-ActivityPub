package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 1dp の線だけでは掴めないので、線の両脇も掴める幅にする。
 * 線の中ほどにつまみを出して、ここを掴めば幅を変えられると分かるようにする
 */
@Composable
internal fun ColumnResizeDivider(
    onResize: (delta: Dp) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnResizeEnd by rememberUpdatedState(onResizeEnd)
    val draggableState = rememberDraggableState { deltaPx ->
        currentOnResize(with(density) { deltaPx.toDp() })
    }
    Box(
        modifier = modifier
            .width(8.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStopped = { currentOnResizeEnd() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            modifier = Modifier
                .size(width = 6.dp, height = 48.dp)
                .background(color = MaterialTheme.colorScheme.outline, shape = CircleShape),
        )
    }
}
