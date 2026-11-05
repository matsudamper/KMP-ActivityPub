package net.matsudamper.kmp.activitypub.frontend.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class PostedAtTextTest {
    private val now = Instant.parse("2026-11-05T12:00:00Z")
    private val timeZone = TimeZone.of("Asia/Tokyo")

    private fun text(publishedAt: String): String = createPostedAtText(publishedAt = publishedAt, now = now, timeZone = timeZone)

    @Test
    fun underOneHourShowsMinutes() {
        assertEquals("0分前", text("2026-11-05T11:59:30Z"))
        assertEquals("59分前", text("2026-11-05T11:00:01Z"))
    }

    @Test
    fun underOneDayShowsHours() {
        assertEquals("1時間前", text("2026-11-05T11:00:00Z"))
        assertEquals("23時間前", text("2026-11-04T12:00:01Z"))
    }

    @Test
    fun sameYearShowsMonthAndDay() {
        assertEquals("11/04", text("2026-11-04T12:00:00Z"))
    }

    @Test
    fun yearIsJudgedInTimeZone() {
        // UTC では 2025 年だが東京では 2026/01/01
        assertEquals("01/01", text("2025-12-31T15:00:00Z"))
        assertEquals("2025/12/31", text("2025-12-31T14:59:59Z"))
    }

    @Test
    fun unparsableIsReturnedAsIs() {
        assertEquals("2026-11-01", text("2026-11-01"))
    }
}
