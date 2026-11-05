package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * Web はブラウザの長押しや右クリックのメニューから保存できるので、アプリでは保存しない
 */
@Composable
internal actual fun rememberImageDownloader(): (imageUrl: String) -> Unit = {}
