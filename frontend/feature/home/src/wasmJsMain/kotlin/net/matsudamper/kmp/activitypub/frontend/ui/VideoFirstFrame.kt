package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLVideoElement

/**
 * Compose の canvas の上に HTML の video 要素を重ね、1 コマ目を出す。
 * 押す・スクロールするは canvas に通すよう pointer-events を切り、見えている範囲だけを clip-path で切り出す
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
        modifier = modifier.onGloballyPositioned { coordinates ->
            val position = coordinates.positionInWindow()
            val visible = coordinates.boundsInWindow()
            val width = coordinates.size.width
            val height = coordinates.size.height
            if (visible.isEmpty) {
                container.style.display = "none"
            } else {
                container.style.display = "block"
                container.style.left = "${position.x / density}px"
                container.style.top = "${position.y / density}px"
                container.style.width = "${width / density}px"
                container.style.height = "${height / density}px"
                val clipTop = (visible.top - position.y) / density
                val clipLeft = (visible.left - position.x) / density
                val clipRight = (position.x + width - visible.right) / density
                val clipBottom = (position.y + height - visible.bottom) / density
                container.style.setProperty("clip-path", "inset(${clipTop}px ${clipRight}px ${clipBottom}px ${clipLeft}px round 12px)")
            }
        },
    )
}

private fun createFirstFrameContainer(videoUrl: String): HTMLDivElement {
    val container = document.createElement("div") as HTMLDivElement
    container.style.cssText = "position:fixed;display:none;pointer-events:none;z-index:1;overflow:hidden;border-radius:12px;background:#000;"
    val video = document.createElement("video") as HTMLVideoElement
    // #t で 1 コマ目より少し先を指すと、再生しなくてもそのコマが描かれる
    video.src = "$videoUrl#t=0.1"
    video.muted = true
    video.preload = "metadata"
    video.setAttribute("playsinline", "")
    video.style.cssText = "width:100%;height:100%;object-fit:cover;"
    // 動画が canvas の上に乗り Compose の再生アイコンを隠すので、HTML でも出す
    val playIcon = document.createElement("div") as HTMLDivElement
    playIcon.textContent = "▶"
    playIcon.style.cssText =
        "position:absolute;left:50%;top:50%;width:48px;height:48px;margin:-24px 0 0 -24px;border-radius:50%;" +
        "background:rgba(0,0,0,0.6);color:#fff;font-size:22px;line-height:48px;text-align:center;"
    container.appendChild(video)
    container.appendChild(playIcon)
    return container
}
