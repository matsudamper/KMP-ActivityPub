package net.matsudamper.kmp.activitypub.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VideoFormatTest {
    @Test
    fun `ftyp の brand で MP4 と MOV を見分ける`() {
        assertEquals(VideoFormat.Mp4, VideoFormat.detect(ftyp("mp42")))
        assertEquals(VideoFormat.QuickTime, VideoFormat.detect(ftyp("qt  ")))
    }

    @Test
    fun `画像の brand は動画にしない`() {
        assertNull(VideoFormat.detect(ftyp("heic")))
        assertNull(VideoFormat.detect(ftyp("avif")))
    }

    @Test
    fun `EBML ヘッダーの DocType が webm なら WebM で、matroska なら受け付けない`() {
        assertEquals(VideoFormat.Webm, VideoFormat.detect(ebml("webm")))
        assertNull(VideoFormat.detect(ebml("matroska")))
    }

    private fun ftyp(brand: String): ByteArray = byteArrayOf(0, 0, 0, 0x18) + "ftyp$brand".encodeToByteArray() + ByteArray(12)

    private fun ebml(docType: String): ByteArray =
        byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x9F.toByte(), 0x42, 0x82.toByte(), (0x80 + docType.length).toByte()) + docType.encodeToByteArray()
}
