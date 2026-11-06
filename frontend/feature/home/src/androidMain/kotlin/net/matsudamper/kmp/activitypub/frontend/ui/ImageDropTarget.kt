package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext

/**
 * 分割画面で他のアプリから画像をドロップされたときに受け取る
 */
@Composable
internal actual fun ImageDropTarget(
    onDrop: (PickedImageFile) -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val activity = LocalActivity.current
    // 読み込みは画面より長く生きるので Activity を持たせない
    val applicationContext = LocalContext.current.applicationContext
    val currentOnDrop by rememberUpdatedState(onDrop)
    val target =
        remember(activity, applicationContext) {
            object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val dragEvent = event.toAndroidDragEvent()
                    val clipData = dragEvent.clipData ?: return false
                    val uri = (0 until clipData.itemCount).firstNotNullOfOrNull { clipData.getItemAt(it).uri } ?: return false
                    // 他のアプリの content URI は許可をもらわないと読めない。許可は Activity が破棄されるまで続く
                    activity?.requestDragAndDropPermissions(dragEvent) ?: return false
                    currentOnDrop(ContentUriImageFile(context = applicationContext, uri = uri))
                    return true
                }
            }
        }
    Box(
        modifier = modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event -> event.mimeTypes().any { it.startsWith("image/") } },
            target = target,
        ),
    ) {
        content()
    }
}
