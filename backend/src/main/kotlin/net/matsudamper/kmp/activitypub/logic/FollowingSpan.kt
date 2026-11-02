package net.matsudamper.kmp.activitypub.logic

import io.opentelemetry.api.common.AttributeKey

internal object FollowingSpan {
    val REQUESTED_URL: AttributeKey<String> = AttributeKey.stringKey("activitypub.following.requested_url")
    val ACTOR_ID: AttributeKey<String> = AttributeKey.stringKey("activitypub.following.actor_id")
    val ACTOR_ID_DIFFERS: AttributeKey<Boolean> = AttributeKey.booleanKey("activitypub.following.actor_id_differs")
    val FOLLOWED: AttributeKey<Boolean> = AttributeKey.booleanKey("activitypub.following.followed")
    val FOLLOW_ID: AttributeKey<String> = AttributeKey.stringKey("activitypub.following.follow_id")
    val ENQUEUED: AttributeKey<Boolean> = AttributeKey.booleanKey("activitypub.following.enqueued")
    val SKIP_REASON: AttributeKey<String> = AttributeKey.stringKey("activitypub.following.skip_reason")
}
