package net.matsudamper.kmp.activitypub.delivery

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode

internal object DeliverySpan {
    val ID: AttributeKey<Long> = AttributeKey.longKey("activitypub.delivery.id")
    val KIND: AttributeKey<String> = AttributeKey.stringKey("activitypub.delivery.kind")
    val SENDER: AttributeKey<String> = AttributeKey.stringKey("activitypub.delivery.sender")
    val INBOX: AttributeKey<String> = AttributeKey.stringKey("activitypub.delivery.inbox")
    val ATTEMPTS: AttributeKey<Long> = AttributeKey.longKey("activitypub.delivery.attempts")
    val OUTCOME: AttributeKey<String> = AttributeKey.stringKey("activitypub.delivery.outcome")
    val FAILURE_REASON: AttributeKey<String> = AttributeKey.stringKey("activitypub.delivery.failure_reason")
    val CLAIMED_COUNT: AttributeKey<Long> = AttributeKey.longKey("activitypub.delivery.claimed_count")
    val RECOVERED_COUNT: AttributeKey<Long> = AttributeKey.longKey("activitypub.delivery.recovered_count")

    fun outcome(value: String) {
        Span.current().setAttribute(OUTCOME, value)
    }

    fun failed(reason: String) {
        Span.current().setAttribute(FAILURE_REASON, reason)
        Span.current().setStatus(StatusCode.ERROR)
    }
}
