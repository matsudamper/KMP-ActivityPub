package net.matsudamper.kmp.activitypub.frontend.ui

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberImagePicker(onPick: (PickedImageFile) -> Unit): () -> Unit {
    // 読み込みは画面より長く生きるので Activity を持たせない
    val contentResolver = LocalContext.current.applicationContext.contentResolver
    val currentOnPick by rememberUpdatedState(onPick)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) currentOnPick(ContentUriImageFile(contentResolver = contentResolver, uri = uri))
        }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
}

private class ContentUriImageFile(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
) : PickedImageFile {
    override suspend fun read(maxBytes: Int): PickedImageFile.ReadResult {
        // 選んだ後に消されたファイルや権限の無いファイルは openInputStream が null を返すか投げる
        val bytes =
            withContext(Dispatchers.IO) {
                runCatching { contentResolver.openInputStream(uri)?.use { it.readAtMost(maxBytes + 1) } }.getOrNull()
            }
        return when {
            bytes == null -> PickedImageFile.ReadResult.Error
            bytes.size > maxBytes -> PickedImageFile.ReadResult.TooLarge
            else -> PickedImageFile.ReadResult.Success(bytes)
        }
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
 * 圧縮後は小さくても画素数の大きい画像があるので、目標の大きさを下回らない範囲で間引いて読む。
 * 写真は撮った向きを EXIF に持つことが多く、BitmapFactory はそれを見ないので、描き直すときに回す
 */
internal actual fun ByteArray.toProfileIconOrNull(sidePx: Int): ProfileIconImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(this, 0, size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val shortestSide = minOf(bounds.outWidth, bounds.outHeight)
    val sampleSize = generateSequence(1) { it * 2 }.takeWhile { shortestSide / it >= sidePx }.lastOrNull() ?: 1
    val decoded = BitmapFactory.decodeByteArray(this, 0, size, BitmapFactory.Options().apply { inSampleSize = sampleSize }) ?: return null

    val cropSide = minOf(decoded.width, decoded.height)
    val targetSide = minOf(cropSide, sidePx)
    val matrix = orientationMatrix(readExifOrientation()).apply {
        val scale = targetSide.toFloat() / cropSide
        postScale(scale, scale)
    }
    val icon =
        Bitmap.createBitmap(
            decoded,
            (decoded.width - cropSide) / 2,
            (decoded.height - cropSide) / 2,
            cropSide,
            cropSide,
            matrix,
            true,
        )
    val png = ByteArrayOutputStream().also { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    return ProfileIconImage(png = png, bitmap = icon.asImageBitmap())
}

private fun ByteArray.readExifOrientation(): Int =
    runCatching { ExifInterface(ByteArrayInputStream(this)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        .getOrDefault(ExifInterface.ORIENTATION_NORMAL)

private fun orientationMatrix(orientation: Int): Matrix =
    Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)

            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)

            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                setRotate(180f)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                setRotate(90f)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)

            ExifInterface.ORIENTATION_TRANSVERSE -> {
                setRotate(-90f)
                postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)

            else -> Unit
        }
    }
