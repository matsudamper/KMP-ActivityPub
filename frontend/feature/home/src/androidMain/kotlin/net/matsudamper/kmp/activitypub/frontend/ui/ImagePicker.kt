package net.matsudamper.kmp.activitypub.frontend.ui

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
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
import java.io.File
import java.io.FileOutputStream
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import coil3.PlatformContext
import coil3.request.ImageRequest
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserNoteImageContent
import okio.BufferedSink
import okio.source

/**
 * 縮めずに展開するので、その大きさを抑える。4 バイト/画素で 256MB 程度。50MP のカメラの写真が入る大きさにする
 */
private const val MAX_DECODED_PIXELS = 64_000_000L

@Composable
internal actual fun rememberImagePicker(onPick: (PickedImageFile) -> Unit): () -> Unit {
    // 読み込みは画面より長く生きるので Activity を持たせない
    val applicationContext = LocalContext.current.applicationContext
    val currentOnPick by rememberUpdatedState(onPick)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) currentOnPick(ContentUriImageFile(context = applicationContext, uri = uri))
        }
    return remember(launcher) {
        { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
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

private class ContentUriImageFile(
    private val context: Context,
    private val uri: Uri,
) : PickedImageFile {
    private val contentResolver: ContentResolver = context.contentResolver

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

    /**
     * 元のファイルは丸ごと読まずに URI から展開し、書き出した WebP はキャッシュのファイルに置く
     */
    override suspend fun toWebp(maxBytes: Int): WebpResult =
        withContext(Dispatchers.IO) {
            // 選んだ後に消されたファイルや権限の無いファイルは openInputStream が null を返すか投げる
            runCatching { writeLosslessWebp(maxBytes) }.getOrDefault(WebpResult.Unreadable)
        }

    private fun writeLosslessWebp(maxBytes: Int): WebpResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openSource().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return WebpResult.Unreadable
        if (bounds.outWidth.toLong() * bounds.outHeight > MAX_DECODED_PIXELS) return WebpResult.TooManyPixels

        val orientation =
            runCatching { openSource().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }
                .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val decoded = openSource().use { BitmapFactory.decodeStream(it) } ?: return WebpResult.Unreadable
        // 向きを変えるだけなので、補間して画素を混ぜない。向きを変えないときは decoded がそのまま返る
        val oriented =
            try {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, orientationMatrix(orientation), false)
            } catch (e: Throwable) {
                decoded.recycle()
                throw e
            }
        try {
            val file = NoteWebpFiles.create(context)
            // Android 10 は WEBP_LOSSLESS が無く、WEBP の品質 100 が可逆になる
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSLESS else Bitmap.CompressFormat.WEBP
            val output = MaxBytesOutputStream(output = FileOutputStream(file), maxBytes = maxBytes)
            val compressed = output.use { runCatching { oriented.compress(format, 100, it) }.getOrDefault(false) }
            if (output.exceeded || !compressed) file.delete()
            return when {
                output.exceeded -> WebpResult.TooLarge
                compressed -> WebpResult.Success(FileWebpImage(file))
                else -> WebpResult.Unreadable
            }
        } finally {
            if (oriented !== decoded) oriented.recycle()
            decoded.recycle()
        }
    }

    private fun openSource(): InputStream = checkNotNull(contentResolver.openInputStream(uri))
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

private class FileWebpImage(
    private val file: File,
) : WebpImage {
    override fun previewRequest(context: PlatformContext): ImageRequest = ImageRequest.Builder(context).data(file).build()

    override suspend fun openContent(): UserNoteImageContent =
        object : UserNoteImageContent {
            override val byteCount: Long = file.length()

            override fun writeTo(sink: BufferedSink) {
                file.source().use { sink.writeAll(it) }
            }
        }

    override fun release() {
        file.delete()
    }
}

/**
 * 書き出した WebP の置き場。投稿画面を閉じる前にプロセスが終わると消されずに残るので、作るときに古いものを消す
 */
private object NoteWebpFiles {
    private const val DIRECTORY_NAME = "note-webp"
    private val STALE_AFTER_MILLIS = TimeUnit.DAYS.toMillis(1)

    fun create(context: Context): File {
        val directory = File(context.cacheDir, DIRECTORY_NAME).apply { mkdirs() }
        val staleBefore = System.currentTimeMillis() - STALE_AFTER_MILLIS
        directory.listFiles()?.filter { it.lastModified() < staleBefore }?.forEach { it.delete() }
        return File.createTempFile("note", ".webp", directory)
    }
}

/**
 * 上限を超えたところで書くのをやめる。compress は書き込み先の例外を握りつぶして false を返すことがあるので、超えたことは [exceeded] で見分ける
 */
private class MaxBytesOutputStream(
    output: OutputStream,
    private val maxBytes: Int,
) : FilterOutputStream(output) {
    var exceeded: Boolean = false
        private set
    private var writtenBytes = 0L

    override fun write(b: Int) {
        checkCapacity(1)
        out.write(b)
        writtenBytes += 1
    }

    override fun write(
        b: ByteArray,
        off: Int,
        len: Int,
    ) {
        checkCapacity(len)
        out.write(b, off, len)
        writtenBytes += len
    }

    private fun checkCapacity(additionalBytes: Int) {
        if (writtenBytes + additionalBytes <= maxBytes) return
        exceeded = true
        throw IOException("WebP が $maxBytes バイトを超えた")
    }
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
