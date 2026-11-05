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
import kotlin.js.Promise
import kotlinx.browser.document
import kotlinx.coroutines.await
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.skia.Codec
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
import org.khronos.webgl.toInt8Array
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

internal actual suspend fun ByteArray.toWebp(maxBytes: Int): WebpResult {
    // 寸法はヘッダーだけで分かるので、画面のスレッドで見ても止まらない
    val pixelCount =
        runCatching {
            val codec = Codec.makeFromData(Data.makeFromBytes(this))
            try {
                codec.width.toLong() * codec.height
            } finally {
                codec.close()
            }
        }.getOrNull() ?: return WebpResult.Unreadable
    if (pixelCount > MAX_DECODED_PIXELS) return WebpResult.TooManyPixels

    val response = requestWebp(worker = webpWorker, source = toInt8Array().buffer, maxBytes = maxBytes).await<JsAny>()
    return when (workerResultKind(response)) {
        "success" -> WebpResult.Success(Int8Array(workerResultBuffer(response)).toByteArray())

        "tooLarge" -> WebpResult.TooLarge

        // Safari などは WebP に書き出せない。画面は止まるが Skia で書き出す
        "unsupported" -> toWebpOnMainThread(maxBytes)

        else -> WebpResult.Unreadable
    }
}

/**
 * Wasm はブラウザの画面のスレッドで動くので、展開と書き出しはブラウザの機能で Worker に任せる。
 * createImageBitmap は既定で EXIF の向きを画素に反映し、canvas の書き出しはメタデータを書き込まない
 */
private val webpWorker: JsAny by lazy {
    createWorker(
        """
        self.onmessage = async (event) => {
            const port = event.ports[0];
            const { source, maxBytes } = event.data;
            try {
                const bitmap = await createImageBitmap(new Blob([source]));
                const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
                canvas.getContext("2d").drawImage(bitmap, 0, 0);
                bitmap.close();
                const blob = await canvas.convertToBlob({ type: "image/webp", quality: 1 });
                if (blob.type !== "image/webp") {
                    port.postMessage({ kind: "unsupported" });
                } else if (blob.size > maxBytes) {
                    port.postMessage({ kind: "tooLarge" });
                } else {
                    const buffer = await blob.arrayBuffer();
                    port.postMessage({ kind: "success", buffer }, [buffer]);
                }
            } catch (e) {
                port.postMessage({ kind: "unreadable" });
            }
        };
        """.trimIndent(),
    )
}

private fun createWorker(source: String): JsAny = js("new Worker(URL.createObjectURL(new Blob([source], { type: 'text/javascript' })))")

/**
 * 応答は呼び出しごとの MessageChannel で受け、並んで変換しても取り違えないようにする
 */
private fun requestWebp(
    worker: JsAny,
    source: ArrayBuffer,
    maxBytes: Int,
): Promise<JsAny> =
    js(
        """
        new Promise((resolve) => {
            const channel = new MessageChannel();
            channel.port1.onmessage = (event) => {
                channel.port1.close();
                resolve(event.data);
            };
            worker.postMessage({ source, maxBytes }, [channel.port2, source]);
        })
        """,
    )

private fun workerResultKind(result: JsAny): String = js("result.kind")

private fun workerResultBuffer(result: JsAny): ArrayBuffer = js("result.buffer")

/**
 * Skia は書き出しを途中で止められないので、書き出し終えてから大きさを見る
 */
private fun ByteArray.toWebpOnMainThread(maxBytes: Int): WebpResult =
    runCatching {
        // 書き出す WebP に EXIF を書き込まず向きの情報も無くなるが、Skia は展開するときに向きを画素に反映している
        val webp = Image.makeFromEncoded(this).encodeWebpBytes()
        when {
            webp == null -> WebpResult.Unreadable
            webp.size > maxBytes -> WebpResult.TooLarge
            else -> WebpResult.Success(webp)
        }
    }.getOrDefault(WebpResult.Unreadable)

/**
 * 画像も書き出した Data も Wasm のメモリを持つので、続けて選ばれても溜まらないよう使い終えたら閉じる
 */
private fun Image.encodeWebpBytes(): ByteArray? {
    try {
        val data = encodeToData(EncodedImageFormat.WEBP, 100) ?: return null
        try {
            return data.bytes
        } finally {
            data.close()
        }
    } finally {
        close()
    }
}

/**
 * 縮めずに原寸で展開するので、その大きさを抑える。4 バイト/画素で 256MB 程度。50MP のカメラの写真が入る大きさにする
 */
private const val MAX_DECODED_PIXELS = 64_000_000L
