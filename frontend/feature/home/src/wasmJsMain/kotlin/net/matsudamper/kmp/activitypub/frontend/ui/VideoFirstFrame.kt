package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLVideoElement

/**
 * HTML の video 要素を Compose の canvas の下に置き、この場所だけ canvas を透明にして見せる。
 * canvas の上に置くと、ダイアログやメニューなど後から描く Compose の要素がすべて video に隠れてしまう。
 * 下に置けば、上に描かれたものは重なり順のとおり video を覆い、押す・スクロールも canvas が受ける
 */
@Composable
internal actual fun VideoFirstFrame(
    videoUrl: String,
    modifier: Modifier,
) {
    val density = LocalDensity.current.density
    val container = remember(videoUrl) { createFirstFrameContainer(videoUrl) }
    DisposableEffect(container) {
        document.body?.appendChild(container)
        onDispose { container.remove() }
    }
    Box(
        modifier = modifier
            .drawBehind { drawRect(Color.Transparent, blendMode = BlendMode.Clear) }
            .onGloballyPositioned { coordinates ->
                val position = coordinates.positionInWindow()
                container.style.display = "block"
                container.style.left = "${position.x / density}px"
                container.style.top = "${position.y / density}px"
                container.style.width = "${coordinates.size.width / density}px"
                container.style.height = "${coordinates.size.height / density}px"
            },
    )
}

private fun createFirstFrameContainer(videoUrl: String): HTMLDivElement {
    val container = document.createElement("div") as HTMLDivElement
    container.style.cssText = "position:fixed;display:none;pointer-events:none;z-index:-1;overflow:hidden;border-radius:12px;background:#000;"
    val video = document.createElement("video") as HTMLVideoElement
    // #t で 1 コマ目より少し先を指すと、再生しなくてもそのコマが描かれる
    video.src = "$videoUrl#t=0.1"
    video.muted = true
    video.preload = "metadata"
    video.setAttribute("playsinline", "")
    video.style.cssText = "width:100%;height:100%;object-fit:cover;"
    container.appendChild(video)
    return container
}
