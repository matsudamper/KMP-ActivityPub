package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
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
    val video = remember(videoUrl) { createFirstFrameVideo(videoUrl) }
    val container = remember(video) { createFirstFrameContainer(video) }
    DisposableEffect(container) {
        document.body?.appendChild(container)
        onDispose { container.remove() }
    }
    Box(
        modifier = modifier
            .drawBehind { drawRect(Color.Transparent, blendMode = BlendMode.Clear) }
            .onGloballyPositioned { coordinates ->
                // 親に切り取られて見えない部分まで置くと、隣の動画の穴からその部分が見えてしまう。
                // 入れ物は見えている範囲だけにし、動画は入れ物の中でずらして全体の大きさで置く
                val position = coordinates.positionInWindow()
                val visible = coordinates.boundsInWindow()
                if (visible.isEmpty) {
                    container.style.display = "none"
                } else {
                    container.style.display = "block"
                    container.style.left = "${visible.left / density}px"
                    container.style.top = "${visible.top / density}px"
                    container.style.width = "${visible.width / density}px"
                    container.style.height = "${visible.height / density}px"
                    video.style.left = "${(position.x - visible.left) / density}px"
                    video.style.top = "${(position.y - visible.top) / density}px"
                    video.style.width = "${coordinates.size.width / density}px"
                    video.style.height = "${coordinates.size.height / density}px"
                }
            },
    )
}

private fun createFirstFrameVideo(videoUrl: String): HTMLVideoElement {
    val video = document.createElement("video") as HTMLVideoElement
    // #t で 1 コマ目より少し先を指すと、再生しなくてもそのコマが描かれる。元の URL に時間指定などがあればそれに任せる
    video.src = if ("#" in videoUrl) videoUrl else "$videoUrl#t=0.1"
    video.muted = true
    video.preload = "metadata"
    video.setAttribute("playsinline", "")
    video.style.cssText = "position:absolute;object-fit:cover;background:#000;"
    return video
}

private fun createFirstFrameContainer(video: HTMLVideoElement): HTMLDivElement {
    val container = document.createElement("div") as HTMLDivElement
    container.style.cssText = "position:fixed;display:none;pointer-events:none;z-index:-1;overflow:hidden;"
    container.appendChild(video)
    return container
}
