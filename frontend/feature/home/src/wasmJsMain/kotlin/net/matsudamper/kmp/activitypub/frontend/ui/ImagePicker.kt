@file:OptIn(ExperimentalWasmJsInterop::class)

package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.js.ExperimentalWasmJsInterop
import kotlinx.browser.document
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.toByteArray
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import org.w3c.files.get

@Composable
internal actual fun rememberImagePicker(
    maxBytes: Int,
    onPick: (ByteArray) -> Unit,
    onTooLarge: () -> Unit,
    onReadError: () -> Unit,
): () -> Unit {
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnTooLarge by rememberUpdatedState(onTooLarge)
    val currentOnReadError by rememberUpdatedState(onReadError)
    return remember {
        {
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.accept = "image/png,image/jpeg,image/gif,image/webp"
            input.addEventListener("change", {
                val file = input.files?.get(0)
                if (file != null && file.size.toDouble() > maxBytes) {
                    currentOnTooLarge()
                } else if (file != null) {
                    val reader = FileReader()
                    reader.onload = {
                        val buffer = reader.result?.unsafeCast<ArrayBuffer>()
                        if (buffer != null) currentOnPick(Int8Array(buffer).toByteArray()) else currentOnReadError()
                        null
                    }
                    reader.onerror = {
                        currentOnReadError()
                        null
                    }
                    reader.readAsArrayBuffer(file)
                }
            })
            input.click()
        }
    }
}

/**
 * 圧縮後は小さくても画素数の大きい画像があり、原寸のまま持つとメモリが足りなくなる。
 * 寸法をヘッダーだけで確かめ、展開できる大きさなら長辺 [MAX_DECODED_SIDE] まで縮めた画像に描き直す
 */
internal actual fun ByteArray.decodeToImageBitmapOrNull(): ImageBitmap? =
    runCatching {
        val codec = Codec.makeFromData(Data.makeFromBytes(this))
        val width = codec.width
        val height = codec.height
        codec.close()
        if (width.toLong() * height > MAX_DECODED_PIXELS) return@runCatching null

        val scale = minOf(1f, MAX_DECODED_SIDE.toFloat() / maxOf(width, height))
        val scaledWidth = maxOf(1, (width * scale).toInt())
        val scaledHeight = maxOf(1, (height * scale).toInt())
        val image = Image.makeFromEncoded(this)
        val surface = Surface.makeRasterN32Premul(scaledWidth, scaledHeight)
        try {
            surface.canvas.drawImageRect(image, Rect.makeWH(scaledWidth.toFloat(), scaledHeight.toFloat()))
            surface.makeImageSnapshot().toComposeImageBitmap()
        } finally {
            surface.close()
            image.close()
        }
    }.getOrNull()

private const val MAX_DECODED_SIDE = 512

/**
 * 縮める前に一度は原寸で展開するので、その大きさを抑える。4 バイト/画素で 200MB 程度
 */
private const val MAX_DECODED_PIXELS = 50_000_000L
