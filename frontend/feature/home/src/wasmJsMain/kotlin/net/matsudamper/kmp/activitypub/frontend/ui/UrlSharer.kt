package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * Web Share API が無いブラウザ（デスクトップの Firefox など）では、共有の代わりにクリップボードへ写す
 */
@Composable
internal actual fun rememberUrlSharer(): (url: String) -> Unit = ::shareUrl

// 共有の画面を閉じただけでも reject されるので、捨てないとコンソールにエラーが出る
private fun shareUrl(url: String): Unit =
    js("{ if (navigator.share) { navigator.share({ url: url }).catch(function () {}); } else if (navigator.clipboard) { navigator.clipboard.writeText(url).catch(function () {}); } }")
