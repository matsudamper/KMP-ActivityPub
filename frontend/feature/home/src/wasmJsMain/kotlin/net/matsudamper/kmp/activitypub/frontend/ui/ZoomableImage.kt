package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.HtmlElementView
import kotlin.math.exp
import kotlin.math.hypot
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLImageElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.dom.pointerevents.PointerEvent

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun ZoomableImage(
    imageUrl: String,
    modifier: Modifier,
) {
    key(imageUrl) {
        HtmlElementView(
            modifier = modifier,
            factory = { createZoomableImageElement(imageUrl) },
        )
    }
}

private fun createZoomableImageElement(imageUrl: String): HTMLDivElement {
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
    HtmlImageZoomGesture(container = container, image = image).bind()
    return container
}

/**
 * ホイールとピンチで拡大縮小し、拡大中はドラッグで動かす。ダブルクリックで拡大と等倍を切り替える
 */
private class HtmlImageZoomGesture(
    private val container: HTMLDivElement,
    private val image: HTMLImageElement,
) {
    private var scale = 1.0
    private var translateX = 0.0
    private var translateY = 0.0
    private val pointerPositions = mutableMapOf<Int, Position>()

    fun bind() {
        container.addEventListener("wheel", ::onWheel)
        container.addEventListener("pointerdown", ::onPointerDown)
        container.addEventListener("pointermove", ::onPointerMove)
        container.addEventListener("pointerup", ::onPointerEnd)
        container.addEventListener("pointercancel", ::onPointerEnd)
        container.addEventListener("dblclick", ::onDoubleClick)
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
        container.setPointerCapture(pointerEvent.pointerId)
        pointerPositions[pointerEvent.pointerId] = positionInContainer(pointerEvent)
    }

    private fun onPointerMove(event: Event) {
        val pointerEvent = event as PointerEvent
        val previous = pointerPositions[pointerEvent.pointerId] ?: return
        val current = positionInContainer(pointerEvent)
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
        pointerPositions.remove((event as PointerEvent).pointerId)
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
    }
}
