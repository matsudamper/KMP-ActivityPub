package net.matsudamper.kmp.activitypub.logic

/**
 * 画像の縦横の画素数。
 *
 * 画像を展開せず、形式ごとのヘッダーから幅と高さだけを読む。
 * 展開するとデコーダーの不具合や、小さなファイルに大きな寸法を書いた画像でのメモリ不足を受ける
 */
internal data class ProfileIconDimensions(
    val width: Int,
    val height: Int,
) {
    companion object {
        /**
         * ヘッダーが壊れているか途中で切れていれば null
         */
        fun read(
            bytes: ByteArray,
            format: ProfileIconFormat,
        ): ProfileIconDimensions? {
            val dimensions =
                when (format) {
                    ProfileIconFormat.Png -> readPng(bytes)
                    ProfileIconFormat.Gif -> readGif(bytes)
                    ProfileIconFormat.Webp -> readWebp(bytes)
                    ProfileIconFormat.Jpeg -> readJpeg(bytes)
                }
            return dimensions?.takeIf { it.width > 0 && it.height > 0 }
        }

        /**
         * 署名の直後が IHDR チャンクで、その先頭に幅と高さがある
         */
        private fun readPng(bytes: ByteArray): ProfileIconDimensions? {
            if (bytes.asciiAt(12, 4) != "IHDR") return null
            val width = bytes.int32BigEndianAt(16) ?: return null
            val height = bytes.int32BigEndianAt(20) ?: return null
            return ProfileIconDimensions(width = width, height = height)
        }

        /**
         * 論理画面の大きさ。中のフレームはこれを超えて描けない
         */
        private fun readGif(bytes: ByteArray): ProfileIconDimensions? {
            val width = bytes.uint16LittleEndianAt(6) ?: return null
            val height = bytes.uint16LittleEndianAt(8) ?: return null
            return ProfileIconDimensions(width = width, height = height)
        }

        /**
         * 先頭のチャンクの種類で幅と高さの置き方が違う
         */
        private fun readWebp(bytes: ByteArray): ProfileIconDimensions? =
            when (bytes.asciiAt(12, 4)) {
                // 非可逆。キーフレームの開始コードの後に 14 ビットずつ
                "VP8 " -> {
                    if (bytes.uint8At(23) != 0x9D || bytes.uint8At(24) != 0x01 || bytes.uint8At(25) != 0x2A) {
                        null
                    } else {
                        val width = bytes.uint16LittleEndianAt(26)
                        val height = bytes.uint16LittleEndianAt(28)
                        if (width == null || height == null) null else ProfileIconDimensions(width = width and 0x3FFF, height = height and 0x3FFF)
                    }
                }

                // 可逆。署名の後に「幅 - 1」と「高さ - 1」が 14 ビットずつ
                "VP8L" -> {
                    val bits = bytes.int32LittleEndianAt(21)
                    if (bytes.uint8At(20) != 0x2F || bits == null) {
                        null
                    } else {
                        ProfileIconDimensions(width = (bits and 0x3FFF) + 1, height = ((bits ushr 14) and 0x3FFF) + 1)
                    }
                }

                // 拡張形式。キャンバスの「幅 - 1」と「高さ - 1」が 24 ビットずつ
                "VP8X" -> {
                    val width = bytes.uint24LittleEndianAt(24)
                    val height = bytes.uint24LittleEndianAt(27)
                    if (width == null || height == null) null else ProfileIconDimensions(width = width + 1, height = height + 1)
                }

                else -> null
            }

        /**
         * マーカーを順にたどり、フレームの開始 (SOF) に書かれた寸法を読む
         */
        private fun readJpeg(bytes: ByteArray): ProfileIconDimensions? {
            var offset = 2
            while (offset < bytes.size) {
                if (bytes.uint8At(offset) != 0xFF) return null
                // マーカーの前には詰め物の 0xFF がいくつ続いてもよい
                while (bytes.uint8At(offset) == 0xFF) offset++
                val marker = bytes.uint8At(offset) ?: return null
                offset++
                if (marker in JPEG_STANDALONE_MARKERS) continue
                // 画像データが始まるまでに SOF が無かった
                if (marker == JPEG_START_OF_SCAN) return null

                val segmentLength = bytes.uint16BigEndianAt(offset) ?: return null
                if (segmentLength < 2) return null
                if (marker in JPEG_START_OF_FRAME_MARKERS) {
                    val height = bytes.uint16BigEndianAt(offset + 3) ?: return null
                    val width = bytes.uint16BigEndianAt(offset + 5) ?: return null
                    return ProfileIconDimensions(width = width, height = height)
                }
                offset += segmentLength
            }
            return null
        }

        private fun ByteArray.uint8At(offset: Int): Int? = getOrNull(offset)?.toInt()?.and(0xFF)

        private fun ByteArray.asciiAt(
            offset: Int,
            length: Int,
        ): String? {
            if (offset < 0 || offset + length > size) return null
            return decodeToString(offset, offset + length)
        }

        private fun ByteArray.uint16BigEndianAt(offset: Int): Int? {
            val high = uint8At(offset) ?: return null
            val low = uint8At(offset + 1) ?: return null
            return (high shl 8) or low
        }

        private fun ByteArray.uint16LittleEndianAt(offset: Int): Int? {
            val low = uint8At(offset) ?: return null
            val high = uint8At(offset + 1) ?: return null
            return (high shl 8) or low
        }

        private fun ByteArray.uint24LittleEndianAt(offset: Int): Int? {
            val low = uint16LittleEndianAt(offset) ?: return null
            val high = uint8At(offset + 2) ?: return null
            return (high shl 16) or low
        }

        private fun ByteArray.int32LittleEndianAt(offset: Int): Int? {
            val low = uint16LittleEndianAt(offset) ?: return null
            val high = uint16LittleEndianAt(offset + 2) ?: return null
            return (high shl 16) or low
        }

        /**
         * 31 ビットを超える値は画像として扱わない
         */
        private fun ByteArray.int32BigEndianAt(offset: Int): Int? {
            val high = uint16BigEndianAt(offset) ?: return null
            val low = uint16BigEndianAt(offset + 2) ?: return null
            return ((high shl 16) or low).takeIf { it >= 0 }
        }

        private const val JPEG_START_OF_SCAN = 0xDA

        /**
         * 長さを持たないマーカー。TEM と RST0〜RST7
         */
        private val JPEG_STANDALONE_MARKERS: Set<Int> = setOf(0x01) + (0xD0..0xD7)

        /**
         * SOF0〜SOF15。0xC4 (DHT)、0xC8 (JPG)、0xCC (DAC) は同じ並びにあるが SOF ではない
         */
        private val JPEG_START_OF_FRAME_MARKERS: Set<Int> = (0xC0..0xCF).toSet() - setOf(0xC4, 0xC8, 0xCC)
    }
}
