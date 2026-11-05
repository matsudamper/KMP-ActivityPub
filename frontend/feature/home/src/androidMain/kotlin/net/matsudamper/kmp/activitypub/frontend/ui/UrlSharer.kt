package net.matsudamper.kmp.activitypub.frontend.ui

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberUrlSharer(): (url: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { url ->
            val send = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, url)
            context.startActivity(Intent.createChooser(send, null))
        }
    }
}
