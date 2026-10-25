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
import org.jetbrains.skia.Image
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
): () -> Unit {
    val currentOnPick by rememberUpdatedState(onPick)
    val currentOnTooLarge by rememberUpdatedState(onTooLarge)
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
                        if (buffer != null) currentOnPick(Int8Array(buffer).toByteArray())
                        null
                    }
                    reader.readAsArrayBuffer(file)
                }
            })
            input.click()
        }
    }
}

internal actual fun ByteArray.decodeToImageBitmapOrNull(): ImageBitmap? =
    runCatching { Image.makeFromEncoded(this).toComposeImageBitmap() }.getOrNull()
