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
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.ImageRequest
import coil3.request.Options
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserNoteImageContent
import okio.Buffer
import okio.BufferedSink
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
import org.w3c.files.Blob
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
            input.accept = PICKABLE_IMAGE_TYPES.joinToString(",")
            input.addEventListener("change", {
                val file = input.files?.get(0)
                if (file != null) currentOnPick(BrowserImageFile(file))
            })
            input.click()
        }
    }
}

internal class BrowserImageFile(
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

    override suspend fun toWebp(maxBytes: Int): WebpResult = file.toWebp(maxBytes)
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

/**
 * 元のファイルは丸ごと読まずに Worker へ渡し、書き出した WebP も Blob のまま持つ
 */
private suspend fun File.toWebp(maxBytes: Int): WebpResult {
    val pixelCount = pixelCountOrNull() ?: return WebpResult.Unreadable
    if (pixelCount > MAX_DECODED_PIXELS) return WebpResult.TooManyPixels

    val response = requestWebp(worker = webpWorker, source = this, maxBytes = maxBytes).await<JsAny>()
    return when (workerResultKind(response)) {
        "success" -> WebpResult.Success(BlobWebpImage(workerResultBlob(response)))

        "tooLarge" -> WebpResult.TooLarge

        // Safari などは WebP に書き出せない。画面は止まるが Skia で書き出す
        "unsupported" -> readBytes(this).toWebpOnMainThread(maxBytes)

        else -> WebpResult.Unreadable
    }
}

/**
 * 寸法はヘッダーだけで分かるので、先頭だけを読む。先頭に収まらないほどメタデータの多い画像だけ全体を読む
 */
private suspend fun File.pixelCountOrNull(): Long? {
    val head = readBytes(sliceHead(this, PIXEL_COUNT_HEADER_BYTES))
    return head.pixelCountOrNull() ?: if (size.toDouble() > PIXEL_COUNT_HEADER_BYTES) readBytes(this).pixelCountOrNull() else null
}

private fun ByteArray.pixelCountOrNull(): Long? =
    runCatching {
        val codec = Codec.makeFromData(Data.makeFromBytes(this))
        try {
            codec.width.toLong() * codec.height
        } finally {
            codec.close()
        }
    }.getOrNull()

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
                const bitmap = await createImageBitmap(source);
                const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
                canvas.getContext("2d").drawImage(bitmap, 0, 0);
                bitmap.close();
                const blob = await canvas.convertToBlob({ type: "image/webp", quality: 1 });
                if (blob.type !== "image/webp") {
                    port.postMessage({ kind: "unsupported" });
                } else if (blob.size > maxBytes) {
                    port.postMessage({ kind: "tooLarge" });
                } else {
                    port.postMessage({ kind: "success", blob });
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
 * 応答は呼び出しごとの MessageChannel で受け、並んで変換しても取り違えないようにする。
 * Blob は Worker に渡しても中身を写さない
 */
private fun requestWebp(
    worker: JsAny,
    source: Blob,
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
            worker.postMessage({ source, maxBytes }, [channel.port2]);
        })
        """,
    )

private fun workerResultKind(result: JsAny): String = js("result.kind")

private fun workerResultBlob(result: JsAny): Blob = js("result.blob")

private fun sliceHead(
    blob: Blob,
    end: Int,
): Blob = js("blob.slice(0, end)")

private fun arrayBufferOf(blob: Blob): Promise<ArrayBuffer> = js("blob.arrayBuffer()")

private fun webpBlobOf(bytes: Int8Array): Blob = js("new Blob([bytes], { type: 'image/webp' })")

private suspend fun readBytes(blob: Blob): ByteArray = Int8Array(arrayBufferOf(blob).await<ArrayBuffer>()).toByteArray()

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
            else -> WebpResult.Success(BlobWebpImage(webpBlobOf(webp.toInt8Array())))
        }
    }.getOrDefault(WebpResult.Unreadable)

/**
 * 中身は Blob に置き、ブラウザに任せる。使わなくなれば捨てられるので、[release] で消すものは無い
 */
private class BlobWebpImage(
    val blob: Blob,
) : WebpImage {
    override fun previewRequest(context: PlatformContext): ImageRequest =
        ImageRequest.Builder(context)
            .data(this)
            .fetcherFactory(BlobWebpFetcher.Factory)
            .build()

    override suspend fun openContent(): UserNoteImageContent {
        val bytes = readBytes(blob)
        return object : UserNoteImageContent {
            override val byteCount: Long = bytes.size.toLong()

            override fun writeTo(sink: BufferedSink) {
                sink.write(bytes)
            }
        }
    }

    override fun release() = Unit
}

/**
 * Coil は Blob を読めないので、表示するときだけ中身を読んで渡す
 */
private class BlobWebpFetcher(
    private val image: BlobWebpImage,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult =
        SourceFetchResult(
            source = ImageSource(source = Buffer().write(readBytes(image.blob)), fileSystem = options.fileSystem),
            mimeType = "image/webp",
            dataSource = DataSource.MEMORY,
        )

    object Factory : Fetcher.Factory<BlobWebpImage> {
        override fun create(
            data: BlobWebpImage,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = BlobWebpFetcher(data, options)
    }
}

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

internal val PICKABLE_IMAGE_TYPES = listOf("image/png", "image/jpeg", "image/gif", "image/webp")

/**
 * JPEG は寸法の前に EXIF などのメタデータが並ぶ。写真のメタデータはふつうこれに収まる
 */
private const val PIXEL_COUNT_HEADER_BYTES = 1024 * 1024
