package net.matsudamper.kmp.activitypub.frontend.screen

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/**
 * 1 時間未満は分、24 時間未満は時間の相対表記。それより前は今年なら MM/dd、去年以前は yyyy/MM/dd
 *
 * 日時として読めない文字列はそのまま返す
 */
internal fun createPostedAtText(publishedAt: String, now: Instant, timeZone: TimeZone): String {
    val publishedInstant = runCatching { Instant.parse(publishedAt) }.getOrNull() ?: return publishedAt
    val elapsed = (now - publishedInstant).coerceAtLeast(Duration.ZERO)
    return when {
        elapsed < 1.hours -> "${elapsed.inWholeMinutes}分前"

        elapsed < 24.hours -> "${elapsed.inWholeHours}時間前"

        else -> {
            val publishedDate = publishedInstant.toLocalDateTime(timeZone).date
            val monthDay = "${publishedDate.month.number.toTwoDigits()}/${publishedDate.day.toTwoDigits()}"
            if (publishedDate.year == now.toLocalDateTime(timeZone).year) {
                monthDay
            } else {
                "${publishedDate.year}/$monthDay"
            }
        }
    }
}

private fun Int.toTwoDigits(): String = toString().padStart(2, '0')
