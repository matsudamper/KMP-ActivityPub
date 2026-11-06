package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Web Share API が無いブラウザ（PC の Firefox など）では、代わりにクリップボードへ写す
 */
@Composable
internal actual fun rememberUrlSharer(onCopiedToClipboard: () -> Unit): (url: String) -> Unit {
    val currentOnCopiedToClipboard by rememberUpdatedState(onCopiedToClipboard)
    return remember {
        { url -> shareUrl(url = url, onCopied = { currentOnCopiedToClipboard() }) }
    }
}

// 共有の画面を閉じただけでも reject されるので、握りつぶして未処理のエラーにしない
private fun shareUrl(
    url: String,
    onCopied: () -> Unit,
): Unit =
    js(
        "navigator.share ? navigator.share({ url: url }).catch(function () {}) : navigator.clipboard.writeText(url).then(function () { onCopied() }, function () {})",
    )
