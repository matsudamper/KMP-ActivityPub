package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import java.time.Instant
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.telemetry.inOpenTelemetrySpan
import net.matsudamper.kotpub.follower.FollowingResponses

internal class RecordedFollowingResponses(
    private val following: FollowingRepository,
    private val clock: Clock,
    openTelemetry: OpenTelemetry,
) : FollowingResponses {
    private val tracer = openTelemetry.getTracer("activitypub-inbox")

    override fun accept(actorId: String, followActivityId: String) {
        respond("Inbox.Accept", actorId, followActivityId, clock.instant())
    }

    override fun reject(actorId: String, followActivityId: String) {
        respond("Inbox.Reject", actorId, followActivityId, null)
    }

    private fun respond(name: String, actorId: String, followActivityId: String, acceptedAt: Instant?) {
        inOpenTelemetrySpan(tracer, name, false) { span ->
            span.setAttribute(InboxSpan.ACTOR, actorId)
            span.setAttribute(InboxSpan.FOLLOW_ID, followActivityId)
            val affectedCount = following.respondToFollow(actorId, followActivityId, acceptedAt)
            span.setAttribute(InboxSpan.AFFECTED_COUNT, affectedCount.toLong())
            if (affectedCount == 0) {
                span.setAttribute(InboxSpan.DISCARD_REASON, "no_matching_account_following")
                span.setStatus(StatusCode.ERROR, "一致する account_following が無い")
            }
        }
    }
}
