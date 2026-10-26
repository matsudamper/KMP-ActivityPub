package net.matsudamper.kmp.activitypub.frontend.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberImagePicker(
    maxBytes: Int,
    onPick: (ByteArray) -> Unit,
    onTooLarge: () -> Unit,
    onReadError: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnTooLarge by rememberUpdatedState(onTooLarge)
    val currentOnReadError by rememberUpdatedState(onReadError)
    val coroutineScope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                coroutineScope.launch {
                    // 選んだ後に消されたファイルや権限の無いファイルは openInputStream が null を返すか投げる
                    val bytes =
                        withContext(Dispatchers.IO) {
                            runCatching { context.contentResolver.openInputStream(uri)?.use { it.readAtMost(maxBytes + 1) } }.getOrNull()
                        }
                    when {
                        bytes == null -> currentOnReadError()
                        bytes.size > maxBytes -> currentOnTooLarge()
                        else -> currentOnPick(bytes)
                    }
                }
            }
        }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

private fun InputStream.readAtMost(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (output.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (read < 0) break
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

/**
 * 圧縮後は小さくても画素数の大きい画像があり、原寸で展開するとメモリが足りなくなる。表示に要る大きさまで間引いて読む
 */
internal actual fun ByteArray.decodeToImageBitmapOrNull(): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val longestSide = maxOf(bounds.outWidth, bounds.outHeight)
    val sampleSize = generateSequence(1) { it * 2 }.first { longestSide / it <= MAX_DECODED_SIDE }
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeByteArray(this, 0, size, options)?.asImageBitmap()
}

private const val MAX_DECODED_SIDE = 512
