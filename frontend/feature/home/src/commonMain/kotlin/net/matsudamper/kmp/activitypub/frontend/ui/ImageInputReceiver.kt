package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * ダイアログで開いた投稿画面の下にもホームの投稿カラムが残る。両方の下書きに入らないよう、最後に置いたものだけが受け取る
 */
internal object ImageInputReceivers {
    private val receivers = mutableListOf<(PickedImageFile) -> Unit>()

    val current: ((PickedImageFile) -> Unit)?
        get() = receivers.lastOrNull()

    fun add(receiver: (PickedImageFile) -> Unit) {
        receivers.add(receiver)
    }

    fun remove(receiver: (PickedImageFile) -> Unit) {
        receivers.remove(receiver)
    }
}

/**
 * 置いている間、[ImageInputArea] にドロップや貼り付けをされた画像を [onReceive] に渡す
 */
@Composable
internal fun ImageInputReceiver(onReceive: (PickedImageFile) -> Unit) {
    val currentOnReceive by rememberUpdatedState(onReceive)
    DisposableEffect(Unit) {
        val receiver: (PickedImageFile) -> Unit = { currentOnReceive(it) }
        ImageInputReceivers.add(receiver)
        onDispose { ImageInputReceivers.remove(receiver) }
    }
}
