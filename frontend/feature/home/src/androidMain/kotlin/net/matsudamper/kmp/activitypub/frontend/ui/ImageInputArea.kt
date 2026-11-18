package net.matsudamper.kmp.activitypub.frontend.ui

import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService

/**
 * 分割画面で他のアプリから落とされた画像と、ハードウェアキーボードの Ctrl+V で貼られた画像を受け取る。
 * Ctrl+V はどこかにフォーカスがある間しか届かない
 */
@Composable
internal actual fun ImageInputArea(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val activity = LocalActivity.current
    // 読み込みは画面より長く生きるので Activity を持たせない
    val applicationContext = LocalContext.current.applicationContext
    val dropTarget =
        remember(activity, applicationContext) {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val receiver = ImageInputReceivers.current ?: return false
                    val dragEvent = event.toAndroidDragEvent()
                    val clipData = dragEvent.clipData ?: return false
                    val uri = (0 until clipData.itemCount).firstNotNullOfOrNull { clipData.getItemAt(it).uri } ?: return false
                    // 他のアプリの content URI は許可をもらわないと読めない。許可は Activity が破棄されるまで続く
                    activity?.requestDragAndDropPermissions(dragEvent) ?: return false
                    receiver(ContentUriImageFile(context = applicationContext, uri = uri))
                    return true
                }
            }
        }
    Box(
        modifier = modifier
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    ImageInputReceivers.current != null && event.mimeTypes().any { it.startsWith("image/") }
                },
                target = dropTarget,
            )
            .onPreviewKeyEvent { event ->
                if (!event.isCtrlPressed || event.key != Key.V) return@onPreviewKeyEvent false
                val receiver = ImageInputReceivers.current ?: return@onPreviewKeyEvent false
                // 画像が無ければ入力欄の文字の貼り付けに任せる
                val uri = applicationContext.clipboardImageUriOrNull() ?: return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) {
                    receiver(ContentUriImageFile(context = applicationContext, uri = uri))
                }
                true
            },
    ) {
        content()
    }
}

private fun android.content.Context.clipboardImageUriOrNull(): Uri? {
    val clipboard = getSystemService<ClipboardManager>() ?: return null
    val description = clipboard.primaryClipDescription ?: return null
    val hasImage = (0 until description.mimeTypeCount).any { description.getMimeType(it).startsWith("image/") }
    if (!hasImage) return null
    val clip = clipboard.primaryClip ?: return null
    return (0 until clip.itemCount).firstNotNullOfOrNull { clip.getItemAt(it).uri }
}
