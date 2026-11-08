package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * 表示している間、Escape キーを受け取る
 */
@Composable
internal expect fun EscapeKeyHandler(
    onPressEscape: () -> Unit,
)
