package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * Web Share API が無いブラウザ（PC の Firefox など）では、代わりにクリップボードへ写す
 */
@Composable
internal actual fun rememberUrlSharer(): (url: String) -> Unit = ::shareUrl

// 共有の画面を閉じただけでも reject されるので、握りつぶして未処理のエラーにしない
private fun shareUrl(url: String): Unit =
    js("(navigator.share ? navigator.share({ url: url }) : navigator.clipboard.writeText(url)).catch(function () {})")
