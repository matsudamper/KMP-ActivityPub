package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.browser.window
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/**
 * SelectionContainer は外側を押されても選択を解除しない。本文ごとに分かれているので、
 * 最後に押された本文以外は作り直して選択を消す
 */
private object LastPressedPostBody {
    var owner: Any? by mutableStateOf(null)

    init {
        // Compose より先に受けるため capture で登録する。押された本文は、この後に Compose の Initial パスで入れ直す
        window.addEventListener("pointerdown", { owner = null }, true)
    }
}

@Composable
internal actual fun PostBodySelectionContainer(
    modifier: Modifier,
    contentPadding: PaddingValues,
    content: @Composable () -> Unit,
) {
    val owner = remember { Any() }
    var selectionGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(owner) {
        snapshotFlow { LastPressedPostBody.owner === owner }
            .drop(1)
            .filter { pressed -> !pressed }
            .collect {
                // capture で null にしてから押された本文が入れ直すまでの間を拾うことがあるので、1 フレーム待って確かめる
                withFrameNanos { }
                if (LastPressedPostBody.owner !== owner) {
                    selectionGeneration++
                }
            }
    }
    Box(
        modifier = modifier.pointerInput(owner) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press) {
                        LastPressedPostBody.owner = owner
                    }
                }
            }
        },
    ) {
        key(selectionGeneration) {
            SelectionContainer {
                Box(modifier = Modifier.padding(contentPadding)) {
                    content()
                }
            }
        }
    }
}
