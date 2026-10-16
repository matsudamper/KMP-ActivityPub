package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

/**
 * 文字列の比較を時刻の比較と一致させるため、秒未満の桁を固定する
 */
internal object StoredInstant {
    private val FORMAT: DateTimeFormatter = DateTimeFormatterBuilder().appendInstant(9).toFormatter()

    fun format(value: Instant): String = FORMAT.format(value)

    fun parse(value: String): Instant = Instant.parse(value)
}
