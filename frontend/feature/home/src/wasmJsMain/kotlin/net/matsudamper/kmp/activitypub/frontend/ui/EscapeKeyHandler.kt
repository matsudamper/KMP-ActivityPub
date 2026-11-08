package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.window
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * 画像を HTML の要素で出していて、ダブルクリックなどで Compose の描画から focus が外れると Dialog に Escape が届かない。
 * window の capture で先に受け、Compose 側でも閉じて 2 回戻らないように伝播を止める
 */
@Composable
internal actual fun EscapeKeyHandler(
    onPressEscape: () -> Unit,
) {
    val currentOnPressEscape by rememberUpdatedState(onPressEscape)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = listener@{ event ->
            val keyboardEvent = event as? KeyboardEvent ?: return@listener
            if (keyboardEvent.key != "Escape") return@listener
            keyboardEvent.preventDefault()
            keyboardEvent.stopPropagation()
            currentOnPressEscape()
        }
        window.addEventListener("keydown", listener, true)
        onDispose { window.removeEventListener("keydown", listener, true) }
    }
}
