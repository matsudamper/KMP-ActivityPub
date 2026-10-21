package net.matsudamper.kmp.activitypub.timeline

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class TimelineCursorTest {
    @Test
    fun `戻すと元の位置になる`() {
        val position = NotePosition(publishedAt = Instant.ofEpochSecond(1_700_000_000, 123_456_789), publicId = PublicNoteId("abc:def"))

        assertEquals(position, TimelineCursor.decode(TimelineCursor.encode(position)))
    }

    @Test
    fun `文字列の大小が時刻と公開 id の順と一致する`() {
        val older = TimelineCursor.encode(NotePosition(publishedAt = Instant.ofEpochSecond(9, 999_999_999), publicId = PublicNoteId("z")))
        val newer = TimelineCursor.encode(NotePosition(publishedAt = Instant.ofEpochSecond(10), publicId = PublicNoteId("a")))
        val newerSameTime = TimelineCursor.encode(NotePosition(publishedAt = Instant.ofEpochSecond(10), publicId = PublicNoteId("b")))

        assertTrue(older < newer)
        assertTrue(newer < newerSameTime)
    }

    @Test
    fun `形が違えば null`() {
        assertNull(TimelineCursor.decode("abc"))
        assertNull(TimelineCursor.decode("00000000001000000000x"))
        assertNull(TimelineCursor.decode("000000000010000000000x"))
    }
}
