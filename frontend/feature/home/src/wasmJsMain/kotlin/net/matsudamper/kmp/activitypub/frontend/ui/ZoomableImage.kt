package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent

internal actual val ComposeCanDrawOverZoomableImage: Boolean = false

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun ZoomableImage(
    imageUrl: String,
    onClickImage: () -> Unit,
    onLongPressImage: () -> Unit,
    onClickOutsideImage: () -> Unit,
    onZoomedChange: (zoomed: Boolean) -> Unit,
    modifier: Modifier,
) {
    val currentOnClickImage by rememberUpdatedState(onClickImage)
    val currentOnClickOutsideImage by rememberUpdatedState(onClickOutsideImage)
    val currentOnZoomedChange by rememberUpdatedState(onZoomedChange)
    key(imageUrl) {
        HtmlElementView(
            modifier = modifier,
            factory = {
                createZoomableImageElement(
                    imageUrl = imageUrl,
                    onClickImage = { currentOnClickImage() },
                    onClickOutsideImage = { currentOnClickOutsideImage() },
                    onZoomedChange = { currentOnZoomedChange(it) },
                )
            },
        )
    }
}

private fun createZoomableImageElement(
    imageUrl: String,
    onClickImage: () -> Unit,
    onClickOutsideImage: () -> Unit,
    onZoomedChange: (zoomed: Boolean) -> Unit,
): HTMLDivElement {
    val container = document.createElement("div") as HTMLDivElement
    container.style.apply {
        width = "100%"
        height = "100%"
        overflowX = "hidden"
        overflowY = "hidden"
        setProperty("touch-action", "none")
        setProperty("user-select", "none")
    }
    val image = document.createElement("img") as HTMLImageElement
    image.src = imageUrl
    image.draggable = false
    image.style.apply {
        width = "100%"
        height = "100%"
        setProperty("object-fit", "contain")
        setProperty("transform-origin", "0 0")
    }
    container.appendChild(image)
    HtmlImageZoomGesture(
        container = container,
        image = image,
        onClickImage = onClickImage,
        onClickOutsideImage = onClickOutsideImage,
        onZoomedChange = onZoomedChange,
    ).bind()
    return container
}

/**
 * ホイールとピンチで拡大縮小し、拡大中はドラッグで動かす。ダブルクリックで拡大と等倍を切り替える。
 * 画像の上か外かを分けてクリックを知らせる
 */
private class HtmlImageZoomGesture(
    private val container: HTMLDivElement,
    private val image: HTMLImageElement,
    private val onClickImage: () -> Unit,
    private val onClickOutsideImage: () -> Unit,
    private val onZoomedChange: (zoomed: Boolean) -> Unit,
) {
    private var scale = 1.0
    private var zoomed = false
    private var gestureStartedSincePointerDown = false
    private var translateX = 0.0
    private var translateY = 0.0
    private val pointerPositions = mutableMapOf<Int, Position>()
    private val uncapturedPointerDownPositions = mutableMapOf<Int, Position>()

    fun bind() {
        container.addEventListener("wheel", ::onWheel)
        container.addEventListener("pointerdown", ::onPointerDown)
        container.addEventListener("pointermove", ::onPointerMove)
        container.addEventListener("pointerup", ::onPointerEnd)
        container.addEventListener("pointercancel", ::onPointerEnd)
        container.addEventListener("dblclick", ::onDoubleClick)
        container.addEventListener("click", ::onClick)
    }

    private fun onWheel(event: Event) {
        val wheelEvent = event as WheelEvent
        wheelEvent.preventDefault()
        zoomAt(
            position = positionInContainer(wheelEvent),
            nextScale = scale * exp(-wheelEvent.deltaY * WHEEL_ZOOM_SENSITIVITY),
        )
    }

    private fun onPointerDown(event: Event) {
        val pointerEvent = event as PointerEvent
        if (pointerEvent.button != MAIN_BUTTON) return
        val position = positionInContainer(pointerEvent)
        if (pointerPositions.isEmpty()) {
            gestureStartedSincePointerDown = false
        }
        pointerPositions[pointerEvent.pointerId] = position
        uncapturedPointerDownPositions[pointerEvent.pointerId] = position
    }

    private fun onPointerMove(event: Event) {
        val pointerEvent = event as PointerEvent
        val previous = pointerPositions[pointerEvent.pointerId] ?: return
        if (pointerEvent.buttons == NO_BUTTONS) {
            // 取り込む前に入れ物の外で離されると pointerup が届かないので、押されていない移動で押下を終える
            onPointerEnd(event)
            return
        }
        val current = positionInContainer(pointerEvent)
        captureWhenGestureStarts(pointerId = pointerEvent.pointerId, current = current)
        val other = pointerPositions.entries.firstOrNull { it.key != pointerEvent.pointerId }?.value
        pointerPositions[pointerEvent.pointerId] = current
        if (other == null) {
            if (scale > MIN_SCALE) {
                translateX += current.x - previous.x
                translateY += current.y - previous.y
                apply()
            }
        } else {
            val previousDistance = previous.distanceTo(other)
            if (previousDistance > 0.0) {
                zoomAt(
                    position = current.midpointTo(other),
                    nextScale = scale * current.distanceTo(other) / previousDistance,
                )
            }
        }
    }

    private fun onPointerEnd(event: Event) {
        val pointerId = (event as PointerEvent).pointerId
        pointerPositions.remove(pointerId)
        uncapturedPointerDownPositions.remove(pointerId)
    }

    /**
     * 取り込むと Firefox は長押しのメニューを入れ物に出し、画像の保存などが出なくなる。
     * 長押し中の指のわずかなぶれでは取り込まず、移動や拡大が始まってから取り込む
     */
    private fun captureWhenGestureStarts(
        pointerId: Int,
        current: Position,
    ) {
        val downPosition = uncapturedPointerDownPositions[pointerId] ?: return
        if (pointerPositions.size < 2 && downPosition.distanceTo(current) < GESTURE_START_DISTANCE) return
        uncapturedPointerDownPositions.remove(pointerId)
        gestureStartedSincePointerDown = true
        container.setPointerCapture(pointerId)
    }

    private fun onClick(event: Event) {
        if (gestureStartedSincePointerDown) return
        if (isOnDisplayedImage(positionInContainer(event as MouseEvent))) {
            onClickImage()
        } else {
            onClickOutsideImage()
        }
    }

    /**
     * 画像は object-fit: contain で入れ物の中央に収まり、左上を起点に拡大してから移動して描かれる
     */
    private fun isOnDisplayedImage(position: Position): Boolean {
        val naturalWidth = image.naturalWidth.toDouble()
        val naturalHeight = image.naturalHeight.toDouble()
        if (naturalWidth <= 0.0 || naturalHeight <= 0.0) return false
        val width = container.clientWidth.toDouble()
        val height = container.clientHeight.toDouble()
        val fitScale = min(width / naturalWidth, height / naturalHeight)
        val fittedWidth = naturalWidth * fitScale
        val fittedHeight = naturalHeight * fitScale
        val left = (width - fittedWidth) / 2
        val top = (height - fittedHeight) / 2
        val x = (position.x - translateX) / scale
        val y = (position.y - translateY) / scale
        return x in left..(left + fittedWidth) && y in top..(top + fittedHeight)
    }

    private fun onDoubleClick(event: Event) {
        val mouseEvent = event as MouseEvent
        zoomAt(
            position = positionInContainer(mouseEvent),
            nextScale = if (scale > MIN_SCALE) MIN_SCALE else DOUBLE_CLICK_SCALE,
        )
    }

    /**
     * [position] の下にある画像の点が動かないように拡大率を変える
     */
    private fun zoomAt(
        position: Position,
        nextScale: Double,
    ) {
        val clampedScale = nextScale.coerceIn(MIN_SCALE, MAX_SCALE)
        val ratio = clampedScale / scale
        translateX = position.x - (position.x - translateX) * ratio
        translateY = position.y - (position.y - translateY) * ratio
        scale = clampedScale
        apply()
    }

    private fun apply() {
        val width = container.clientWidth.toDouble()
        val height = container.clientHeight.toDouble()
        // 入れ物の外に余白が出るところまでは動かさない
        translateX = translateX.coerceIn(width * (1 - scale), 0.0)
        translateY = translateY.coerceIn(height * (1 - scale), 0.0)
        image.style.setProperty("transform", "translate(${translateX}px, ${translateY}px) scale($scale)")
        val nextZoomed = scale > MIN_SCALE
        if (nextZoomed != zoomed) {
            zoomed = nextZoomed
            onZoomedChange(nextZoomed)
        }
    }

    private fun positionInContainer(event: MouseEvent): Position {
        val rect = container.getBoundingClientRect()
        return Position(x = event.clientX - rect.left, y = event.clientY - rect.top)
    }

    private data class Position(
        val x: Double,
        val y: Double,
    ) {
        fun distanceTo(other: Position): Double = hypot(x - other.x, y - other.y)

        fun midpointTo(other: Position): Position = Position(x = (x + other.x) / 2, y = (y + other.y) / 2)
    }

    private companion object {
        const val MIN_SCALE = 1.0
        const val MAX_SCALE = 5.0
        const val DOUBLE_CLICK_SCALE = 2.5
        const val WHEEL_ZOOM_SENSITIVITY = 0.002
        const val MAIN_BUTTON: Short = 0
        const val NO_BUTTONS: Short = 0
        const val GESTURE_START_DISTANCE = 8.0
    }
}
