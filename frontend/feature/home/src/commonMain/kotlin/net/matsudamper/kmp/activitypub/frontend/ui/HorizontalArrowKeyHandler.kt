package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * 表示している間、左右の矢印キーを受け取る
 */
@Composable
internal expect fun HorizontalArrowKeyHandler(
    onPressLeft: () -> Unit,
    onPressRight: () -> Unit,
)
