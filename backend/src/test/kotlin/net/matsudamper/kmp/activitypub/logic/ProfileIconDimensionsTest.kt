package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class ProfileIconDimensionsTest {
    @Test
    fun `各形式のヘッダーから寸法を読む`() {
        listOf(
            "401x3.png" to ProfileIconDimensions(width = 401, height = 3),
            "5x402.gif" to ProfileIconDimensions(width = 5, height = 402),
            "403x7.jpg" to ProfileIconDimensions(width = 403, height = 7),
            "9x404-progressive.jpg" to ProfileIconDimensions(width = 9, height = 404),
            "405x11-lossy.webp" to ProfileIconDimensions(width = 405, height = 11),
            "13x406-lossless.webp" to ProfileIconDimensions(width = 13, height = 406),
            "407x15-extended.webp" to ProfileIconDimensions(width = 407, height = 15),
        ).forEach { (fileName, expected) ->
            val bytes = TestProfileIcons.read(fileName)
            val format = checkNotNull(ProfileIconFormat.detect(bytes)) { fileName }

            assertEquals(expected, ProfileIconDimensions.read(bytes, format), fileName)
        }
    }

    @Test
    fun `途中で切れたヘッダーは読めない扱いにする`() {
        // 署名は残り、寸法の途中で切れる長さ
        listOf(
            "401x3.png" to 22,
            "5x402.gif" to 9,
            "403x7.jpg" to 16,
            "405x11-lossy.webp" to 29,
            "13x406-lossless.webp" to 24,
            "407x15-extended.webp" to 29,
        ).forEach { (fileName, length) ->
            val bytes = TestProfileIcons.read(fileName)
            val format = checkNotNull(ProfileIconFormat.detect(bytes)) { fileName }

            assertNull(ProfileIconDimensions.read(bytes.copyOf(length), format), fileName)
        }
    }

    @Test
    fun `JPEG で寸法の前に画像データが始まれば読めない扱いにする`() {
        val startOfScanOnly = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xDA.toByte(), 0x00, 0x08)

        assertNull(ProfileIconDimensions.read(startOfScanOnly, ProfileIconFormat.Jpeg))
    }

    @Test
    fun `JPEG のセグメント長が 2 未満なら先へ進まずに読めない扱いにする`() {
        val brokenLength = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x00)

        assertNull(ProfileIconDimensions.read(brokenLength, ProfileIconFormat.Jpeg))
    }
}
