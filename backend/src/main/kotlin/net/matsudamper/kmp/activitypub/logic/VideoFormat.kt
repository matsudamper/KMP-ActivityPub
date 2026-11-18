package net.matsudamper.kmp.activitypub.logic

/**
 * 投稿の動画として受け付ける形式。
 *
 * [ImageFormat] と同じく送ってきた側の申告は信じず、先頭のバイト列で決める
 */
internal enum class VideoFormat(
    val contentType: String,
) {
    Mp4("video/mp4"),
    QuickTime("video/quicktime"),
    Webm("video/webm"),
    ;

    companion object {
        /**
         * [detect] が見る先頭のバイト数。WebM は EBML ヘッダーの DocType まで見る
         */
        const val SIGNATURE_BYTES: Int = 64

        private val FTYP_SIGNATURE = "ftyp".encodeToByteArray()
        private val EBML_SIGNATURE = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte())
        private val WEBM_DOC_TYPE = "webm".encodeToByteArray()
        private const val QUICKTIME_BRAND = "qt  "

        /**
         * HEIC や AVIF も同じ ftyp の箱で始まるので、動画の brand だけを通す
         */
        private val MP4_BRANDS = setOf("isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V ", "dash", "mmp4", "MSNV")

        fun detect(bytes: ByteArray): VideoFormat? =
            when {
                bytes.startsWith(FTYP_SIGNATURE, offset = 4) -> detectFtypBrand(bytes)
                bytes.startsWith(EBML_SIGNATURE) && bytes.contains(WEBM_DOC_TYPE) -> Webm
                else -> null
            }

        private fun detectFtypBrand(bytes: ByteArray): VideoFormat? {
            if (bytes.size < 12) return null
            return when (bytes.decodeToString(startIndex = 8, endIndex = 12)) {
                QUICKTIME_BRAND -> QuickTime
                in MP4_BRANDS -> Mp4
                else -> null
            }
        }

        private fun ByteArray.contains(target: ByteArray): Boolean = (0..size - target.size).any { offset -> startsWith(target, offset) }

        private fun ByteArray.startsWith(
            prefix: ByteArray,
            offset: Int = 0,
        ): Boolean {
            if (size < offset + prefix.size) return false
            return prefix.indices.all { index -> this[offset + index] == prefix[index] }
        }
    }
}
