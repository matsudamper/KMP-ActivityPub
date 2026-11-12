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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * カラムの右の仕切りを左右に動かして変えた幅。端末にも残さないので、画面を作り直すと元に戻る
 */
@Stable
internal class ColumnWidthState(
    private val defaultWidth: Dp,
    private val minWidth: Dp,
    private val maxWidth: Dp,
) {
    private val widths = mutableStateMapOf<String, Dp>()

    fun width(columnKey: String): Dp = widths[columnKey] ?: defaultWidth

    fun resize(columnKey: String, delta: Dp) {
        widths[columnKey] = (width(columnKey) + delta).coerceIn(minWidth, maxWidth)
    }
}

@Composable
internal fun rememberColumnWidthState(defaultWidth: Dp, minWidth: Dp, maxWidth: Dp): ColumnWidthState {
    return remember(defaultWidth, minWidth, maxWidth) {
        ColumnWidthState(defaultWidth = defaultWidth, minWidth = minWidth, maxWidth = maxWidth)
    }
}

/**
 * 1dp の線だけでは掴めないので、線の両脇も掴める幅にする。
 * 線の中ほどにつまみを出して、ここを掴めば幅を変えられると分かるようにする
 */
@Composable
internal fun ColumnResizeDivider(
    state: ColumnWidthState,
    columnKey: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val draggableState = rememberDraggableState { deltaPx ->
        state.resize(columnKey = columnKey, delta = with(density) { deltaPx.toDp() })
    }
    Box(
        modifier = modifier
            .width(8.dp)
            .fillMaxHeight()
            .pointerHoverIcon(PointerIcon.Hand)
            .draggable(state = draggableState, orientation = Orientation.Horizontal),
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
