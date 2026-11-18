package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import kotlinx.browser.document
import org.w3c.dom.DragEvent
import org.w3c.dom.clipboard.ClipboardEvent
import org.w3c.dom.events.Event
import org.w3c.files.FileList
import org.w3c.files.get

/**
 * Compose は 1 枚の canvas に描くので、置いた範囲は分からない。ウィンドウのどこへの入力も受け取る。
 * 入力欄は HTML の textarea なので、そこでの貼り付けも document まで上がってくる
 */
@Composable
internal actual fun ImageInputArea(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    DisposableEffect(Unit) {
        DocumentImageInputListeners.attach()
        onDispose { DocumentImageInputListeners.detach() }
    }
    Box(modifier = modifier) {
        content()
    }
}

/**
 * 画面とダイアログの両方に [ImageInputArea] を置くので、document への登録は 1 つにまとめる
 */
private object DocumentImageInputListeners {
    private var attachedCount = 0

    private val pasteListener: (Event) -> Unit = listener@{ event ->
        val receiver = ImageInputReceivers.current ?: return@listener
        val media = (event as? ClipboardEvent)?.clipboardData?.files?.firstMediaOrNull() ?: return@listener
        // 画像のファイルを textarea に貼らせると、ファイル名などが文字として入ることがある
        event.preventDefault()
        receiver(BrowserImageFile(media))
    }

    // dragover を止めないと drop が来ない
    private val dragOverListener: (Event) -> Unit = listener@{ event ->
        if (ImageInputReceivers.current == null) return@listener
        val dragEvent = event as? DragEvent ?: return@listener
        val dataTransfer = dragEvent.dataTransfer ?: return@listener
        val hasFiles = (0 until dataTransfer.types.length).any { dataTransfer.types[it]?.toString() == "Files" }
        if (!hasFiles) return@listener
        dragEvent.preventDefault()
        dataTransfer.dropEffect = "copy"
    }

    private val dropListener: (Event) -> Unit = listener@{ event ->
        val receiver = ImageInputReceivers.current ?: return@listener
        val dragEvent = event as? DragEvent ?: return@listener
        val files = dragEvent.dataTransfer?.files ?: return@listener
        if (files.length == 0) return@listener
        // 画像や動画でないファイルも、止めないとブラウザがそのページを離れて開いてしまう
        dragEvent.preventDefault()
        val media = files.firstMediaOrNull() ?: return@listener
        receiver(BrowserImageFile(media))
    }

    fun attach() {
        if (attachedCount == 0) {
            document.addEventListener("paste", pasteListener)
            document.addEventListener("dragover", dragOverListener)
            document.addEventListener("drop", dropListener)
        }
        attachedCount++
    }

    fun detach() {
        attachedCount--
        if (attachedCount == 0) {
            document.removeEventListener("paste", pasteListener)
            document.removeEventListener("dragover", dragOverListener)
            document.removeEventListener("drop", dropListener)
        }
    }
}

private fun FileList.firstMediaOrNull() =
    (0 until length).firstNotNullOfOrNull { index ->
        get(index)?.takeIf { it.type in PICKABLE_IMAGE_TYPES || it.type in PICKABLE_VIDEO_TYPES }
    }
