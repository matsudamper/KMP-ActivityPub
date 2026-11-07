package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * キーは focus を持つ部品にしか届かないので、見えない部品に focus を持たせて受ける
 */
@Composable
internal actual fun HorizontalArrowKeyHandler(
    onPressLeft: () -> Unit,
    onPressRight: () -> Unit,
) {
    val currentOnPressLeft by rememberUpdatedState(onPressLeft)
    val currentOnPressRight by rememberUpdatedState(onPressRight)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
    Box(
        modifier = Modifier
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        currentOnPressLeft()
                        true
                    }

                    Key.DirectionRight -> {
                        currentOnPressRight()
                        true
                    }

                    else -> false
                }
            }
            .focusRequester(focusRequester)
            .focusable(),
    )
}
