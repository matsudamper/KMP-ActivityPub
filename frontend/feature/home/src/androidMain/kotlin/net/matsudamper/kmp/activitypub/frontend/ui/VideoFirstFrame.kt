package net.matsudamper.kmp.activitypub.frontend.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaMetadataRetriever は必要な部分だけを取りに行くので、動画全体を落とさずに 1 コマ目を取れる
 */
@Composable
internal actual fun VideoFirstFrame(
    videoUrl: String,
    modifier: Modifier,
) {
    val frame by produceState(initialValue = FirstFrameCache.get(videoUrl), videoUrl) {
        // URL が変わっても produceState は前の値を持ち続けるので、前の動画のコマを出さないよう置き換えてから取る
        val cached = FirstFrameCache.get(videoUrl)
        value = cached
        if (cached == null) {
            value = withContext(Dispatchers.IO) { retrieveFirstFrame(videoUrl) }?.also { FirstFrameCache.put(videoUrl, it) }
        }
    }
    val currentFrame = frame
    if (currentFrame != null) {
        Image(
            bitmap = currentFrame.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

// スクロールで戻るたびに取り直さないよう、取れたコマを覚えておく
private object FirstFrameCache : LruCache<String, Bitmap>(FIRST_FRAME_CACHE_BYTES) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

private const val FIRST_FRAME_CACHE_BYTES = 16 * 1024 * 1024

// カードに出すだけなので、元の解像度のままでは持たない
private const val FIRST_FRAME_MAX_SIZE_PX = 480

private const val FIRST_FRAME_TIME_US = 100_000L

private fun retrieveFirstFrame(videoUrl: String): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        // 投稿画面で選んだ動画はキャッシュのファイルにある。URL を渡す方は file の URI を読めるとは限らない
        val localPath = Uri.parse(videoUrl).takeIf { it.scheme == ContentResolver.SCHEME_FILE }?.path
        if (localPath != null) retriever.setDataSource(localPath) else retriever.setDataSource(videoUrl, mapOf())
        retriever.getScaledFrameAtTime(
            FIRST_FRAME_TIME_US,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            FIRST_FRAME_MAX_SIZE_PX,
            FIRST_FRAME_MAX_SIZE_PX,
        )
    } catch (e: RuntimeException) {
        // 相手のサーバーが応答しない・形式を読めないときは 1 コマ目を出さず、再生アイコンだけのカードにする
        null
    } finally {
        retriever.release()
    }
}
