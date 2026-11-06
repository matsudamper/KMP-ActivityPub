package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * [content] に画像がドロップされたら [onDrop] に渡す。画像でないものはドロップさせない
 */
@Composable
internal expect fun ImageDropTarget(
    onDrop: (PickedImageFile) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
