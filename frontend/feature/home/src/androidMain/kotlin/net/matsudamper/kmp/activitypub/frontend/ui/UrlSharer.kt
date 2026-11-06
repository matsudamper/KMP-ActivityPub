package net.matsudamper.kmp.activitypub.frontend.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Android はいつも共有の画面を出せるので、[onCopiedToClipboard] は呼ばない
 */
@Composable
internal actual fun rememberUrlSharer(onCopiedToClipboard: () -> Unit): (url: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            val sendIntent = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, url)
            context.startActivity(Intent.createChooser(sendIntent, null))
        }
    }
}
