package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 画面いっぱいに収めて表示し、拡大縮小と拡大中の移動ができる画像
 */
@Composable
internal expect fun ZoomableImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
)
