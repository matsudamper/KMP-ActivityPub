package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * 返した関数に URL を渡すと、端末の共有の画面を開く
 */
@Composable
internal expect fun rememberUrlSharer(): (url: String) -> Unit
