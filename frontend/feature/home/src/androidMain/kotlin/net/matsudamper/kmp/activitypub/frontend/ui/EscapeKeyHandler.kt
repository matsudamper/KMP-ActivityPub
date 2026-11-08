package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * Android は画像を Compose で描いて focus が外れないので、Escape は Dialog が受けて閉じる
 */
@Composable
internal actual fun EscapeKeyHandler(
    onPressEscape: () -> Unit,
) = Unit
