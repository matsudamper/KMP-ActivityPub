package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

/**
 * 縦に長い画像から、ヘッダーに使う範囲を上下に動かして選ぶ
 */
@Composable
internal fun HeaderCropContent(
    content: ProfileEditScreenUiState.Content.HeaderCrop,
    listener: ProfileEditScreenUiState.Listener,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "上下にドラッグして、ヘッダーに使う範囲を選んでください",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            HeaderCropArea(content = content, onMove = listener::onHeaderCropMoved)
        }
        Button(
            onClick = listener::onClickHeaderCropConfirm,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Text("決定")
        }
    }
}

@Composable
private fun HeaderCropArea(
    content: ProfileEditScreenUiState.Content.HeaderCrop,
    onMove: (Float) -> Unit,
) {
    // ドラッグの途中で値が変わっても、受け取り直さずに最新の位置から動かす
    val cropTopRatio by rememberUpdatedState(content.cropTopRatio)
    val cropHeightRatio by rememberUpdatedState(content.cropHeightRatio)
    val currentOnMoved by rememberUpdatedState(onMove)
    Box(
        modifier = Modifier.aspectRatio(
            ratio = content.image.width.toFloat() / content.image.height,
            matchHeightConstraintsFirst = true,
        ),
    ) {
        Image(
            bitmap = content.image,
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
            val cropHeight = size.height * content.cropHeightRatio
            val cropTop = (size.height - cropHeight) * content.cropTopRatio
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

private val CROP_OUTSIDE_SCRIM = Color.Black.copy(alpha = 0.6f)
