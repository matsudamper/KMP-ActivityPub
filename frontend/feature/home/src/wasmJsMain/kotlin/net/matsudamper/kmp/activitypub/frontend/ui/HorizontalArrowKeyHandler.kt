package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/**
 * 画像を HTML の要素で出していて、押すと Compose の描画から focus が外れるので、document で受ける
 */
@Composable
internal actual fun HorizontalArrowKeyHandler(
    onPressLeft: () -> Unit,
    onPressRight: () -> Unit,
) {
    val currentOnPressLeft by rememberUpdatedState(onPressLeft)
    val currentOnPressRight by rememberUpdatedState(onPressRight)
    DisposableEffect(Unit) {
        val listener: (Event) -> Unit = listener@{ event ->
            val keyboardEvent = event as? KeyboardEvent ?: return@listener
            when (keyboardEvent.key) {
                "ArrowLeft" -> {
                    keyboardEvent.preventDefault()
                    currentOnPressLeft()
                }

                "ArrowRight" -> {
                    keyboardEvent.preventDefault()
                    currentOnPressRight()
                }
            }
        }
        document.addEventListener("keydown", listener)
        onDispose { document.removeEventListener("keydown", listener) }
    }
}
