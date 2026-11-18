package net.matsudamper.kmp.activitypub.frontend.ui

import android.content.Context
import android.graphics.Color
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.Color as ComposeColor
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

@Composable
internal actual fun VideoPlayerDialog(
    videoUrl: String,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(ComposeColor.Black)) {
            AndroidView(
                factory = { context -> createVideoWebView(context, videoUrl) },
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                onRelease = { webView -> webView.destroy() },
            )
            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = ComposeColor.White.copy(alpha = 0.2f),
                    contentColor = ComposeColor.White,
                ),
            ) {
                Icon(imageVector = AppIcons.Close, contentDescription = "閉じる")
            }
        }
    }
}

// 動画の再生に JavaScript は要らないので有効にしない
private fun createVideoWebView(context: Context, videoUrl: String): WebView = WebView(context).apply {
    setBackgroundColor(Color.BLACK)
    settings.mediaPlaybackRequiresUserGesture = false
    loadDataWithBaseURL(
        null,
        """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>html,body{margin:0;height:100%;background:#000;display:flex;align-items:center;justify-content:center}video{max-width:100%;max-height:100%}</style>
        </head><body><video src="${videoUrl.escapeHtmlAttribute()}" controls autoplay playsinline></video></body></html>
        """.trimIndent(),
        "text/html",
        "UTF-8",
        null,
    )
}

private fun String.escapeHtmlAttribute(): String = this
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
