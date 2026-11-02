package net.matsudamper.kmp.activitypub.timeline

import io.opentelemetry.api.common.AttributeKey

internal object TimelineSpan {
    val MEMBER: AttributeKey<String> = AttributeKey.stringKey("activitypub.timeline.member")
    val DELIVERED_COUNT: AttributeKey<Long> = AttributeKey.longKey("activitypub.timeline.delivered_count")
    val SKIPPED_COUNT: AttributeKey<Long> = AttributeKey.longKey("activitypub.timeline.skipped_count")
    val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("activitypub.timeline.outcome")
    val CACHE_READ: AttributeKey<Boolean> = AttributeKey.booleanKey("activitypub.timeline.cache_read")
    val SQL_READ: AttributeKey<Boolean> = AttributeKey.booleanKey("activitypub.timeline.sql_read")
    val REBUILT: AttributeKey<Boolean> = AttributeKey.booleanKey("activitypub.timeline.rebuilt")
}
