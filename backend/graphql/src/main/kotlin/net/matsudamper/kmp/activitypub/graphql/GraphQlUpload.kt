package net.matsudamper.kmp.activitypub.graphql

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * multipart で送られてきた `Upload` の中身。形式はここでは信じず、使う側が先頭のバイト列で決める。
 * 画像は大きく同時にも送られるので、メモリには持たず一時ファイルに置く。ファイルは応答を返した後に消す
 *
 * @param size [file] のバイト数
 */
class GraphQlUpload(
    private val file: Path,
    val size: Long,
) {
    fun openStream(): InputStream = Files.newInputStream(file)
}
