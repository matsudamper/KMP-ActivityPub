package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.zIndex

/**
 * 横に並んだタブやカラムを、長押ししてから左右に動かして並べ替える。
 * 動かしている間は掴んだものだけを指に付いてこさせ、離したときに指の下にあった位置へ移す
 */
@Stable
internal class HorizontalReorderState {
    var draggingIndex: Int? by mutableStateOf(null)
        private set
    var dragOffsetX: Float by mutableFloatStateOf(0f)
        private set
    private val itemRanges = mutableStateMapOf<Int, ClosedFloatingPointRange<Float>>()

    fun updateItemRange(index: Int, start: Float, width: Float) {
        itemRanges[index] = start..(start + width)
    }

    /**
     * 消したタブやカラムの位置が残ると、離したときにそこへ移そうとするので外す
     */
    fun retainItems(count: Int) {
        itemRanges.keys.filter { it >= count }.forEach { itemRanges.remove(it) }
    }

    fun startDrag(index: Int) {
        draggingIndex = index
        dragOffsetX = 0f
    }

    fun drag(deltaX: Float) {
        dragOffsetX += deltaX
    }

    /**
     * @return 動かす先。動かさないときは null
     */
    fun endDrag(): Pair<Int, Int>? {
        val fromIndex = draggingIndex
        val draggedRange = fromIndex?.let { itemRanges[it] }
        val draggedCenter = draggedRange?.let { (it.start + it.endInclusive) / 2 + dragOffsetX }
        cancelDrag()
        if (fromIndex == null || draggedCenter == null) return null
        val toIndex = itemRanges.entries.firstOrNull { draggedCenter in it.value }?.key
            ?: farthestIndexInDirection(draggedCenter)
            ?: return null
        return if (toIndex == fromIndex) null else fromIndex to toIndex
    }

    fun cancelDrag() {
        draggingIndex = null
        dragOffsetX = 0f
    }

    /**
     * 端より外まで動かしたときは、その側の端へ移す
     */
    private fun farthestIndexInDirection(draggedCenter: Float): Int? {
        val first = itemRanges.minByOrNull { it.value.start } ?: return null
        val last = itemRanges.maxByOrNull { it.value.endInclusive } ?: return null
        return when {
            draggedCenter < first.value.start -> first.key
            draggedCenter > last.value.endInclusive -> last.key
            else -> null
        }
    }
}

@Composable
internal fun rememberHorizontalReorderState(): HorizontalReorderState = remember { HorizontalReorderState() }

/**
 * 並べ替える 1 つ分の全体に付ける。位置を覚え、動かしている間は指に付いてこさせる
 */
internal fun Modifier.horizontalReorderItem(state: HorizontalReorderState, index: Int): Modifier = this
    .onGloballyPositioned { coordinates ->
        state.updateItemRange(index = index, start = coordinates.positionInParent().x, width = coordinates.size.width.toFloat())
    }
    .zIndex(if (state.draggingIndex == index) 1f else 0f)
    .graphicsLayer {
        translationX = if (state.draggingIndex == index) state.dragOffsetX else 0f
    }

/**
 * 長押しして動かす場所に付ける。タブなら全体、カラムなら見出し
 */
@Composable
internal fun Modifier.horizontalReorderHandle(
    state: HorizontalReorderState,
    index: Int,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
): Modifier {
    val currentOnMove by rememberUpdatedState(onMove)
    return pointerInput(state, index) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.startDrag(index) },
            onDragEnd = {
                val move = state.endDrag()
                if (move != null) currentOnMove(move.first, move.second)
            },
            onDragCancel = { state.cancelDrag() },
            onDrag = { change, dragAmount ->
                change.consume()
                state.drag(dragAmount.x)
            },
        )
    }
}
