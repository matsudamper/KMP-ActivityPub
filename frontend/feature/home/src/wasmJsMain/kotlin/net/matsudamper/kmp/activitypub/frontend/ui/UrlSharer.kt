package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable

/**
 * Web Share API が無いブラウザ（デスクトップの Firefox など）や、共有先が無く断られたときは、クリップボードへ写す
 */
@Composable
internal actual fun rememberUrlSharer(): (url: String) -> Unit = ::shareUrl

// 共有の画面を閉じただけでも AbortError で reject されるので、それは失敗として扱わない
private fun shareUrl(url: String): Unit =
    js(
        """{
            const copy = function () { if (navigator.clipboard) { navigator.clipboard.writeText(url).catch(function () {}); } };
            if (navigator.share) {
                navigator.share({ url: url }).catch(function (e) { if (e.name !== 'AbortError') { copy(); } });
            } else {
                copy();
            }
        }""",
    )
