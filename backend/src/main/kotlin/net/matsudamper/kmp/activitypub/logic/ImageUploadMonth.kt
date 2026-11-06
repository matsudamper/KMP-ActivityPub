package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 画像の置き場の先頭に付ける、置いた年月（UTC）の `{yyyy}/{MM}`。
 * 過ぎた月の下には新しい画像が増えないので、月ごとに丸ごとコピーすれば増分のバックアップになる
 */
internal object ImageUploadMonth {
    private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM").withZone(ZoneOffset.UTC)

    fun prefix(uploadedAt: Instant): String = FORMATTER.format(uploadedAt)
}
