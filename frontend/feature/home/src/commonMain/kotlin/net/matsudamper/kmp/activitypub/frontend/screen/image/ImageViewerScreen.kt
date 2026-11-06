package net.matsudamper.kmp.activitypub.frontend.screen.image

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.ui.ComposeCanDrawOverZoomableImage
import net.matsudamper.kmp.activitypub.frontend.ui.ZoomableImage
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import net.matsudamper.kmp.activitypub.frontend.ui.rememberImageDownloader

@Composable
internal fun ImageViewerScreen(
    imageUrl: String,
    navigator: Navigator,
) {
    val downloadImage = rememberImageDownloader()
    val uiState = remember(imageUrl, navigator, downloadImage) {
        ImageViewerScreenUiState(
            imageUrl = imageUrl,
            listener = object : ImageViewerScreenUiState.Listener {
                override fun onClickClose() {
                    navigator.back()
                }

                override fun onClickOutsideImage() {
                    navigator.back()
                }

                override fun onClickConfirmDownload() {
                    downloadImage(imageUrl)
                }
            },
        )
    }
    ImageViewerContent(uiState = uiState)
}

@Composable
internal fun ImageViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    if (ComposeCanDrawOverZoomableImage) {
        OverlayImageViewerContent(uiState = uiState)
    } else {
        ImageOnlyViewerContent(uiState = uiState)
    }
}

@Composable
internal fun DownloadConfirmDialog(
    onClickDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("画像をダウンロードしますか？") },
        confirmButton = {
            TextButton(onClick = onClickDownload) {
                Text("ダウンロード")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

/**
 * 拡大したら閉じるボタンを隠し、画像のタップで出し入れする。画像の長押しで保存するか尋ねる
 */
@Composable
private fun OverlayImageViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    var closeButtonVisible by remember { mutableStateOf(true) }
    var downloadDialogVisible by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
    ) {
        ZoomableImage(
            imageUrl = uiState.imageUrl,
            onClickImage = { closeButtonVisible = !closeButtonVisible },
            onLongPressImage = { downloadDialogVisible = true },
            onClickOutsideImage = uiState.listener::onClickOutsideImage,
            onZoomedChange = { zoomed -> closeButtonVisible = !zoomed },
            modifier = Modifier.fillMaxSize(),
        )
        if (closeButtonVisible) {
            CloseButton(
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(8.dp),
            )
        }
    }
    if (downloadDialogVisible) {
        DownloadConfirmDialog(
            onClickDownload = {
                downloadDialogVisible = false
                uiState.listener.onClickConfirmDownload()
            },
            onDismiss = { downloadDialogVisible = false },
        )
    }
}

/**
 * 閉じるボタンを画像に重ねられないので出さず、画像の外を押して閉じる
 */
@Composable
private fun ImageOnlyViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    ZoomableImage(
        imageUrl = uiState.imageUrl,
        onClickImage = {},
        onLongPressImage = {},
        onClickOutsideImage = uiState.listener::onClickOutsideImage,
        onZoomedChange = {},
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
    )
}

@Composable
private fun CloseButton(
    uiState: ImageViewerScreenUiState,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = uiState.listener::onClickClose,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White,
        ),
        modifier = modifier,
    ) {
        Icon(
            imageVector = AppIcons.Close,
            contentDescription = "閉じる",
        )
    }
}
