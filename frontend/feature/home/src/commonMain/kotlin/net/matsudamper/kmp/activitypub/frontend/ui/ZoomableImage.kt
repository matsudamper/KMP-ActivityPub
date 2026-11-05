package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Web は画像を HTML の要素で Compose の描画より手前に置くので、画像の上に Compose の部品を重ねられない
 */
internal expect val ComposeCanDrawOverZoomableImage: Boolean

/**
 * 画面いっぱいに収めて表示し、拡大縮小と拡大中の移動ができる画像
 */
@Composable
internal expect fun ZoomableImage(
    imageUrl: String,
    onClickImage: () -> Unit,
    onLongPressImage: () -> Unit,
    onClickOutsideImage: () -> Unit,
    onZoomedChange: (zoomed: Boolean) -> Unit,
    modifier: Modifier = Modifier,
)
