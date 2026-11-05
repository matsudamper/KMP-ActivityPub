package net.matsudamper.kmp.activitypub.frontend.ui

import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 進み具合と完了は DownloadManager が通知に出す。Android 10 以降は権限なしで Download に保存できる
 */
@Composable
internal actual fun rememberImageDownloader(): (imageUrl: String) -> Unit {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        { imageUrl ->
            val downloadManager = applicationContext.getSystemService(DownloadManager::class.java)
            val fileName = URLUtil.guessFileName(imageUrl, null, null)
            val request = DownloadManager.Request(Uri.parse(imageUrl))
                .setTitle(fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            downloadManager.enqueue(request)
        }
    }
}
