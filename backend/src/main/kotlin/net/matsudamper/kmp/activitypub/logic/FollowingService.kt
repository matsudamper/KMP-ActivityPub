package net.matsudamper.kmp.activitypub.logic

import java.net.URI
import java.time.Clock
import kotlinx.coroutines.CancellationException
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.inOpenTelemetrySuspendSpan
import org.slf4j.LoggerFactory

class FollowingService(
    private val accounts: AccountRepository,
    private val following: FollowingRepository,
    private val delivery: FollowDelivery,
    private val invalidator: TimelineInvalidator,
    private val localDomain: String,
    private val clock: Clock,
    openTelemetry: OpenTelemetry = OpenTelemetryInitializer.get(),
) {
    private val logger = LoggerFactory.getLogger(FollowingService::class.java)
    private val tracer = openTelemetry.getTracer("activitypub-following")

    fun isFollowing(accountId: AccountId?, actorUri: String?): Boolean =
        accountId != null && actorUri != null && following.contains(accountId, actorUri)

    suspend fun setFollowing(accountId: AccountId?, actorUri: String, followed: Boolean): Boolean =
        inOpenTelemetrySuspendSpan(tracer, "Following.setFollowing", false) { span ->
            span.setAttribute(FollowingSpan.REQUESTED_URL, actorUri)
            span.setAttribute(FollowingSpan.FOLLOWED, followed)
            setFollowingInSpan(accountId, actorUri, followed, span)
        }

    private suspend fun setFollowingInSpan(accountId: AccountId?, actorUri: String, followed: Boolean, span: Span): Boolean {
        if (accountId == null || accounts.findById(accountId) == null) {
            span.setAttribute(FollowingSpan.ENQUEUED, false)
            span.setAttribute(FollowingSpan.SKIP_REASON, "account_not_found")
            return false
        }
        val uri = runCatching { URI(actorUri) }.getOrNull()
        if (uri == null || uri.scheme !in listOf("https", "http") || uri.host == null || uri.host.equals(localDomain, ignoreCase = true)) {
            span.setAttribute(FollowingSpan.ENQUEUED, false)
            span.setAttribute(FollowingSpan.SKIP_REASON, "invalid_remote_url")
            return false
        }
        if (!followed) {
            val followId = following.followId(accountId, actorUri)
            if (followId != null) span.setAttribute(FollowingSpan.FOLLOW_ID, followId)
            if (following.remove(accountId, actorUri)) {
                invalidator.invalidate(accountId)
                try {
                    delivery.send(accountId, actorUri, false, followId)
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    span.recordException(failure)
                    span.setAttribute(FollowingSpan.ENQUEUED, false)
                    span.setAttribute(FollowingSpan.SKIP_REASON, "undo_delivery_failed")
                    logger.warn("ローカルのフォローを解除したが Undo を積めなかった: $actorUri", failure)
                }
            } else {
                span.setAttribute(FollowingSpan.ENQUEUED, false)
                span.setAttribute(FollowingSpan.SKIP_REASON, "following_not_found")
            }
        } else if (following.add(accountId, actorUri, clock.instant())) {
            try {
                delivery.send(accountId, actorUri, true, null)
            } catch (failure: Exception) {
                following.remove(accountId, actorUri)
                throw failure
            }
            invalidator.invalidate(accountId)
        } else {
            span.setAttribute(FollowingSpan.ENQUEUED, false)
            span.setAttribute(FollowingSpan.SKIP_REASON, "already_following")
        }
        return true
    }
}

interface FollowDelivery {
    suspend fun send(accountId: AccountId, actorUri: String, followed: Boolean, followId: String?)
}

fun interface TimelineInvalidator {
    fun invalidate(accountId: AccountId)
}
