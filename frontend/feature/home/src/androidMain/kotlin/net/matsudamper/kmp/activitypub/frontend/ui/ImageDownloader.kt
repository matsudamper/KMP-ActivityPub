package net.matsudamper.kmp.activitypub.frontend.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 保存を頼んだ直後に画面を閉じても止めないよう、画面より長く生きるスコープで頼む
 */
private val imageDownloadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 進み具合と完了は DownloadManager が通知に出す。Android 10 以降は権限なしで Download に保存できる
 */
@Composable
internal actual fun rememberImageDownloader(): (imageUrl: String) -> Unit {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        { imageUrl ->
            imageDownloadScope.launch {
                enqueueImageDownload(
                    context = applicationContext,
                    imageUrl = imageUrl,
                    mimeType = fetchMimeTypeOrNull(imageUrl),
                )
            }
        }
    }
}

private fun enqueueImageDownload(
    context: Context,
    imageUrl: String,
    mimeType: String?,
) {
    val fileName = imageFileName(imageUrl = imageUrl, mimeType = mimeType)
    val request = DownloadManager.Request(Uri.parse(imageUrl))
        .setTitle(fileName)
        .setMimeType(mimeType)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
    context.getSystemService(DownloadManager::class.java).enqueue(request)
}

/**
 * 画像の URL は拡張子が無いか、中継のために .bin で終わる。
 * 保存先を決めて頼むと DownloadManager は拡張子を直さないので、応答の MIME 型から付け直す
 */
private fun imageFileName(
    imageUrl: String,
    mimeType: String?,
): String {
    val guessedFileName = URLUtil.guessFileName(imageUrl, null, mimeType)
    val extension = mimeType?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
    return if (extension == null) {
        guessedFileName
    } else {
        "${guessedFileName.substringBeforeLast('.')}.$extension"
    }
}

/**
 * HEAD に答えない配信元もあるので GET で頼み、ヘッダーだけ読んで切る
 */
private fun fetchMimeTypeOrNull(imageUrl: String): String? {
    val connection = try {
        URL(imageUrl).openConnection() as HttpURLConnection
    } catch (_: IOException) {
        return null
    }
    return try {
        connection.contentType?.substringBefore(';')?.trim()
    } catch (_: IOException) {
        null
    } finally {
        connection.disconnect()
    }
}
