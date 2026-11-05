package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * 返した関数に画像の URL を渡すと、端末に保存する
 */
@Composable
internal expect fun rememberImageDownloader(): (imageUrl: String) -> Unit
