package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 縦に長い画像から、ヘッダーに使う範囲を上下に動かして選ぶ。入力の途中で閉じると選んだ範囲が消えるので、外側を押しても閉じない
 */
@Composable
internal fun HeaderCropDialog(
    dialog: ProfileEditScreenUiState.HeaderCropDialog,
    listener: ProfileEditScreenUiState.Listener,
) {
    Dialog(
        onDismissRequest = listener::onClickHeaderCropCancel,
        properties = DialogProperties(dismissOnClickOutside = false),
    ) {
        HeaderCropDialogContent(dialog = dialog, listener = listener)
    }
}

@Composable
internal fun HeaderCropDialogContent(
    dialog: ProfileEditScreenUiState.HeaderCropDialog,
    listener: ProfileEditScreenUiState.Listener,
) {
    Surface(
        modifier = Modifier.widthIn(max = 560.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = "ヘッダーの範囲を選ぶ", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "上下にドラッグして、ヘッダーに使う範囲を選んでください",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = CROP_AREA_MAX_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                HeaderCropArea(dialog = dialog, onMove = listener::onHeaderCropMoved)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                TextButton(onClick = listener::onClickHeaderCropCancel) {
                    Text("キャンセル")
                }
                TextButton(onClick = listener::onClickHeaderCropConfirm) {
                    Text("決定")
                }
            }
        }
    }
}

@Composable
private fun HeaderCropArea(
    dialog: ProfileEditScreenUiState.HeaderCropDialog,
    onMove: (Float) -> Unit,
) {
    // ドラッグの途中で値が変わっても、受け取り直さずに最新の位置から動かす
    val cropTopRatio by rememberUpdatedState(dialog.cropTopRatio)
    val cropHeightRatio by rememberUpdatedState(dialog.cropHeightRatio)
    val currentOnMoved by rememberUpdatedState(onMove)
    Box(
        modifier = Modifier.aspectRatio(
            ratio = dialog.image.width.toFloat() / dialog.image.height,
            matchHeightConstraintsFirst = true,
        ),
    ) {
        Image(
            bitmap = dialog.image,
            contentDescription = "選んだ画像",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        val movableHeight = size.height * (1f - cropHeightRatio)
                        if (movableHeight > 0f) {
                            change.consume()
                            currentOnMoved(cropTopRatio + dragAmount / movableHeight)
                        }
                    }
                },
        ) {
            val cropHeight = size.height * dialog.cropHeightRatio
            val cropTop = (size.height - cropHeight) * dialog.cropTopRatio
            drawRect(color = CROP_OUTSIDE_SCRIM, size = Size(size.width, cropTop))
            drawRect(
                color = CROP_OUTSIDE_SCRIM,
                topLeft = Offset(0f, cropTop + cropHeight),
                size = Size(size.width, size.height - cropTop - cropHeight),
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(0f, cropTop),
                size = Size(size.width, cropHeight),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

private val CROP_AREA_MAX_HEIGHT = 480.dp
private val CROP_OUTSIDE_SCRIM = Color.Black.copy(alpha = 0.6f)
