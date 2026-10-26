package net.matsudamper.kmp.activitypub.logic

/**
 * プロフィール画像として受け付ける形式。
 *
 * 送ってきた側の申告は信じず、先頭のバイト列で決める。
 * 画像以外を画像の種類で配ると、相手やブラウザがそれを解釈してしまう
 */
internal enum class ProfileIconFormat(
    val contentType: String,
) {
    Png("image/png"),
    Jpeg("image/jpeg"),
    Gif("image/gif"),
    Webp("image/webp"),
    ;

    companion object {
        fun detect(bytes: ByteArray): ProfileIconFormat? =
            when {
                bytes.startsWith(PNG_SIGNATURE) -> Png
                bytes.startsWith(JPEG_SIGNATURE) -> Jpeg
                bytes.startsWith(GIF87_SIGNATURE) || bytes.startsWith(GIF89_SIGNATURE) -> Gif
                bytes.startsWith(RIFF_SIGNATURE) && bytes.startsWith(WEBP_SIGNATURE, offset = 8) -> Webp
                else -> null
            }

        private fun ByteArray.startsWith(
            prefix: ByteArray,
            offset: Int = 0,
        ): Boolean {
            if (size < offset + prefix.size) return false
            return prefix.indices.all { index -> this[offset + index] == prefix[index] }
        }

        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        private val GIF87_SIGNATURE = "GIF87a".encodeToByteArray()
        private val GIF89_SIGNATURE = "GIF89a".encodeToByteArray()
        private val RIFF_SIGNATURE = "RIFF".encodeToByteArray()
        private val WEBP_SIGNATURE = "WEBP".encodeToByteArray()
    }
}
