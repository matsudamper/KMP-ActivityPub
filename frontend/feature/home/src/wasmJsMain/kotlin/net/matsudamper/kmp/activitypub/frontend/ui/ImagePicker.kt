@file:OptIn(ExperimentalWasmJsInterop::class)

package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.coroutines.resume
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.math.roundToInt
import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Color
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
internal actual fun rememberImagePicker(onPick: (PickedImageFile) -> Unit): () -> Unit {
    val currentOnPick by rememberUpdatedState(onPick)
    return remember {
        {
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = "image/png,image/jpeg,image/gif,image/webp"
            input.addEventListener("change", {
                val file = input.files?.get(0)
                if (file != null) currentOnPick(BrowserImageFile(file))
            })
            input.click()
        }
    }
}

private class BrowserImageFile(
    private val file: File,
) : PickedImageFile {
    override suspend fun read(maxBytes: Int): PickedImageFile.ReadResult {
        if (file.size.toDouble() > maxBytes) return PickedImageFile.ReadResult.TooLarge

        return suspendCancellableCoroutine { continuation ->
            val reader = FileReader()
            reader.onload = {
                val buffer = reader.result?.unsafeCast<ArrayBuffer>()
                continuation.resume(
                    if (buffer != null) PickedImageFile.ReadResult.Success(Int8Array(buffer).toByteArray()) else PickedImageFile.ReadResult.Error,
                )
                null
            }
            reader.onerror = {
                continuation.resume(PickedImageFile.ReadResult.Error)
                null
            }
            continuation.invokeOnCancellation { reader.abort() }
            reader.readAsArrayBuffer(file)
        }
    }
}

/**
 * 圧縮後は小さくても画素数の大きい画像があり、展開するとメモリが足りなくなる。
 * 寸法をヘッダーだけで確かめ、展開できる大きさのものだけを描き直す
 */
internal actual fun ByteArray.toProfileIconOrNull(sidePx: Int): ProfileIconImage? =
    runCatching {
        val codec = Codec.makeFromData(Data.makeFromBytes(this))
        val pixelCount = codec.width.toLong() * codec.height
        codec.close()
        if (pixelCount > MAX_DECODED_PIXELS) return@runCatching null

        val image = Image.makeFromEncoded(this)
        val cropSide = minOf(image.width, image.height)
        val targetSide = minOf(cropSide, sidePx)
        val surface = Surface.makeRasterN32Premul(targetSide, targetSide)
        try {
            surface.canvas.drawImageRect(
                image,
                Rect.makeXYWH(
                    ((image.width - cropSide) / 2).toFloat(),
                    ((image.height - cropSide) / 2).toFloat(),
                    cropSide.toFloat(),
                    cropSide.toFloat(),
                ),
                Rect.makeWH(targetSide.toFloat(), targetSide.toFloat()),
                // 既定の最近傍で大きく縮めるとギザギザになる
                FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
                null,
                true,
            )
            val icon = surface.makeImageSnapshot()
            val png = icon.encodeToData(EncodedImageFormat.PNG)?.bytes ?: return@runCatching null
            ProfileIconImage(png = png, bitmap = icon.toComposeImageBitmap())
        } finally {
            surface.close()
            image.close()
        }
    }.getOrNull()

internal actual fun ByteArray.toPostImageOrNull(
    maxSidePx: Int,
    maxBytes: Int,
): PostImage? =
    runCatching {
        val codec = Codec.makeFromData(Data.makeFromBytes(this))
        val pixelCount = codec.width.toLong() * codec.height
        codec.close()
        if (pixelCount > MAX_DECODED_PIXELS) return@runCatching null

        val image = Image.makeFromEncoded(this)
        try {
            val longestSide = maxOf(image.width, image.height)
            postImageSideCandidates(sourceLongestSide = longestSide, maxSidePx = maxSidePx)
                .asSequence()
                .mapNotNull { side -> image.toOpaqueJpegOrNull(scale = side.toFloat() / longestSide) }
                .firstOrNull { it.jpeg.size <= maxBytes }
        } finally {
            image.close()
        }
    }.getOrNull()

private fun Image.toOpaqueJpegOrNull(scale: Float): PostImage? {
    val targetWidth = maxOf(1, (width * scale).roundToInt())
    val targetHeight = maxOf(1, (height * scale).roundToInt())
    val surface = Surface.makeRasterN32Premul(targetWidth, targetHeight)
    try {
        // JPEG は透明を持てず、そのまま書き出すと透明な部分が黒くなる
        surface.canvas.clear(Color.WHITE)
        surface.canvas.drawImageRect(
            this,
            Rect.makeWH(width.toFloat(), height.toFloat()),
            Rect.makeWH(targetWidth.toFloat(), targetHeight.toFloat()),
            // 既定の最近傍で大きく縮めるとギザギザになる
            FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
            null,
            true,
        )
        val snapshot = surface.makeImageSnapshot()
        val jpeg = snapshot.encodeToData(EncodedImageFormat.JPEG, POST_IMAGE_JPEG_QUALITY)?.bytes ?: return null
        return PostImage(jpeg = jpeg, bitmap = snapshot.toComposeImageBitmap())
    } finally {
        surface.close()
    }
}

private const val POST_IMAGE_JPEG_QUALITY = 85

/**
 * 縮める前に一度は原寸で展開するので、その大きさを抑える。4 バイト/画素で 200MB 程度
 */
private const val MAX_DECODED_PIXELS = 50_000_000L
