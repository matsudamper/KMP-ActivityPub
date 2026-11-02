package net.matsudamper.kmp.activitypub.federation

import io.opentelemetry.api.common.AttributeKey

internal object InboxSpan {
    val FOLLOW_ID: AttributeKey<String> = AttributeKey.stringKey("activitypub.inbox.follow_id")
    val AFFECTED_COUNT: AttributeKey<Long> = AttributeKey.longKey("activitypub.inbox.affected_count")
    val FOLLOWING_ACCOUNT_COUNT: AttributeKey<Long> = AttributeKey.longKey("activitypub.inbox.following_account_count")
    val LOCAL_RECIPIENT_COUNT: AttributeKey<Long> = AttributeKey.longKey("activitypub.inbox.local_recipient_count")
    val OBJECT_URI: AttributeKey<String> = AttributeKey.stringKey("activitypub.inbox.object_uri")
    val ACTOR: AttributeKey<String> = AttributeKey.stringKey("activitypub.inbox.actor")
    val SAVED: AttributeKey<Boolean> = AttributeKey.booleanKey("activitypub.inbox.saved")
    val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("activitypub.inbox.outcome")
    val DISCARD_REASON: AttributeKey<String> = AttributeKey.stringKey("activitypub.inbox.discard_reason")
}
