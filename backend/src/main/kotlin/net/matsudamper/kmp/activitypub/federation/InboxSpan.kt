package net.matsudamper.kmp.activitypub.federation

import io.opentelemetry.api.common.AttributeKey

internal object InboxSpan {
    val OBJECT_URI: AttributeKey<String> = AttributeKey.stringKey("activitypub.inbox.object_uri")
    val ACTOR: AttributeKey<String> = AttributeKey.stringKey("activitypub.inbox.actor")
    val SAVED: AttributeKey<Boolean> = AttributeKey.booleanKey("activitypub.inbox.saved")
    val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("activitypub.inbox.outcome")
    val DISCARD_REASON: AttributeKey<String> = AttributeKey.stringKey("activitypub.inbox.discard_reason")
}
