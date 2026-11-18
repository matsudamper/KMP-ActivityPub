package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.Node
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * Compose の canvas の上に HTML の要素を重ねる。canvas の下にある Compose の閉じるボタンは押せないので、閉じるボタンも HTML で置く
 */
@Composable
internal actual fun VideoPlayerDialog(
    videoUrl: String,
    onDismissRequest: () -> Unit,
) {
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    DisposableEffect(videoUrl) {
        val overlay = document.createElement("div") as HTMLDivElement
        overlay.style.cssText = "position:fixed;inset:0;z-index:2147483647;background:#000;display:flex;align-items:center;justify-content:center;"
        val video = document.createElement("video") as HTMLVideoElement
        video.src = videoUrl
        video.controls = true
        video.autoplay = true
        video.setAttribute("playsinline", "")
        video.style.cssText = "max-width:100%;max-height:100%;"
        val closeButton = document.createElement("button") as HTMLButtonElement
        closeButton.textContent = "✕"
        closeButton.setAttribute("aria-label", "閉じる")
        closeButton.style.cssText =
            "position:absolute;top:16px;right:16px;width:48px;height:48px;border:none;border-radius:50%;" +
            "background:#333;color:#fff;font-size:24px;cursor:pointer;"
        closeButton.addEventListener("click", { currentOnDismissRequest() })
        val keyListener: (Event) -> Unit = { event ->
            if ((event as? KeyboardEvent)?.key == "Escape") currentOnDismissRequest()
        }
        // Tab で後ろの要素へフォーカスが出たら、プレイヤーの中へ戻す
        val focusInListener: (Event) -> Unit = { event ->
            val target = event.target as? Node
            if (target == null || !overlay.contains(target)) closeButton.focus()
        }
        overlay.appendChild(video)
        overlay.appendChild(closeButton)
        overlay.setAttribute("role", "dialog")
        overlay.setAttribute("aria-modal", "true")
        overlay.setAttribute("aria-label", "動画")
        document.body?.appendChild(overlay)
        document.addEventListener("keydown", keyListener)
        document.addEventListener("focusin", focusInListener)
        // キーボードの操作が後ろの canvas に行かないよう、開いたらフォーカスを移す
        closeButton.focus()
        onDispose {
            document.removeEventListener("keydown", keyListener)
            document.removeEventListener("focusin", focusInListener)
            video.pause()
            // pause と要素の削除だけでは読み込みが続くことがあるので、src を外して止める
            video.removeAttribute("src")
            video.load()
            overlay.remove()
        }
    }
}
