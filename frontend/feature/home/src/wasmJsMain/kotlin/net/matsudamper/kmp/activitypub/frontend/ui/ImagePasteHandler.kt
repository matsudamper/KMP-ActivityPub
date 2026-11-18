package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.clipboard.ClipboardEvent
import org.w3c.dom.events.Event
import org.w3c.files.get

/**
 * ダイアログで開いた投稿画面の下にもホームの投稿カラムが残る。両方の下書きに入らないよう、最後に置いたものだけが受け取る
 */
private val activePasteHandlerKeys = mutableListOf<Any>()

/**
 * 入力欄は HTML の textarea なので、そこでの貼り付けも document まで上がってくる
 */
@Composable
internal actual fun ImagePasteHandler(onPaste: (PickedImageFile) -> Unit) {
    val currentOnPaste by rememberUpdatedState(onPaste)
    DisposableEffect(Unit) {
        val handlerKey = Any()
        activePasteHandlerKeys.add(handlerKey)
        val listener: (Event) -> Unit = listener@{ event ->
            if (activePasteHandlerKeys.lastOrNull() !== handlerKey) return@listener
            val files = (event as? ClipboardEvent)?.clipboardData?.files ?: return@listener
            val image = (0 until files.length).firstNotNullOfOrNull { index -> files[index]?.takeIf { it.type in PICKABLE_IMAGE_TYPES } }
                ?: return@listener
            // 画像のファイルを textarea に貼らせると、ファイル名などが文字として入ることがある
            event.preventDefault()
            currentOnPaste(BrowserImageFile(image))
        }
        document.addEventListener("paste", listener)
        onDispose {
            document.removeEventListener("paste", listener)
            activePasteHandlerKeys.remove(handlerKey)
        }
    }
}
