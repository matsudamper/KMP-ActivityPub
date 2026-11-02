package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import java.util.UUID
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.FollowDelivery
import net.matsudamper.kmp.activitypub.logic.FollowingSpan
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.inOpenTelemetrySuspendSpan
import net.matsudamper.kotpub.activity.FollowActivities
import net.matsudamper.kotpub.activity.OutgoingActivity
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.json.AppJson
import org.slf4j.LoggerFactory

internal class QueuedFollowDelivery(
    private val accounts: AccountService,
    private val directory: ActorDirectory,
    private val actors: RemoteActors,
    private val storedActors: RemoteActorRepository,
    private val following: FollowingRepository,
    private val queue: DeliveryQueueRepository,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
    openTelemetry: OpenTelemetry = OpenTelemetryInitializer.get(),
) : FollowDelivery {
    private val logger = LoggerFactory.getLogger(QueuedFollowDelivery::class.java)
    private val tracer = openTelemetry.getTracer("activitypub-following")

    override suspend fun send(accountId: AccountId, actorUri: String, followed: Boolean, followId: String?) {
        inOpenTelemetrySuspendSpan(tracer, "Following.enqueue", false) { span ->
            span.setAttribute(FollowingSpan.REQUESTED_URL, actorUri)
            span.setAttribute(FollowingSpan.FOLLOWED, followed)
            span.setAttribute(FollowingSpan.ENQUEUED, false)
            if (followId != null) span.setAttribute(FollowingSpan.FOLLOW_ID, followId)
            try {
                sendInSpan(accountId, actorUri, followed, followId, span)
            } catch (failure: Exception) {
                span.setAttribute(FollowingSpan.SKIP_REASON, "delivery_failed")
                throw failure
            }
        }
    }

    private suspend fun sendInSpan(accountId: AccountId, actorUri: String, followed: Boolean, followId: String?, span: Span) {
        val account = checkNotNull(accounts.account(accountId))
        val sender = checkNotNull(directory.resolve(account.username))
        if (followed) {
            val remote = checkNotNull(actors.findActor(actorUri)) { "相手の inbox を取得できない" }
            span.setAttribute(FollowingSpan.ACTOR_ID, remote.actorId)
            span.setAttribute(FollowingSpan.ACTOR_ID_DIFFERS, actorUri != remote.actorId)
            storedActors.save(remote.toRecord(), clock.instant())
            if (actorUri != remote.actorId) storedActors.remove(actorUri)
            if (!following.contains(accountId, actorUri)) {
                span.setAttribute(FollowingSpan.SKIP_REASON, "following_removed")
                return
            }
            val canonicalFollowingAdded = actorUri != remote.actorId && following.add(accountId, remote.actorId, clock.instant())
            if (actorUri != remote.actorId) {
                following.remove(accountId, actorUri)
                if (!canonicalFollowingAdded) {
                    val recordedId = following.followId(accountId, remote.actorId)
                    if (recordedId != null) span.setAttribute(FollowingSpan.FOLLOW_ID, recordedId)
                    span.setAttribute(FollowingSpan.SKIP_REASON, "canonical_actor_already_followed")
                    return
                }
            }
            val id = "${sender.actorId}/follows/${UUID.randomUUID()}"
            val follow = FollowActivities.follow(id, sender.actorId, remote.actorId)
            val enqueued = try {
                following.recordActivityAndEnqueueFollow(
                    accountId,
                    remote.actorId,
                    id,
                    remote.inbox,
                    AppJson.encodeToString(OutgoingActivity.serializer(), follow),
                    clock.instant(),
                )
            } catch (failure: Exception) {
                if (canonicalFollowingAdded) following.remove(accountId, remote.actorId)
                throw failure
            }
            if (!enqueued) {
                span.setAttribute(FollowingSpan.SKIP_REASON, "following_removed")
                return
            }
            span.setAttribute(FollowingSpan.FOLLOW_ID, id)
        } else {
            val inbox = storedActors.findByUri(actorUri)?.inbox
            if (inbox == null || followId == null) {
                span.setAttribute(FollowingSpan.SKIP_REASON, if (inbox == null) "stored_inbox_missing" else "follow_id_missing")
                logger.warn("保存済みの inbox または Follow の ID が無いので Undo を諦める: $actorUri")
                return
            }
            val follow = FollowActivities.follow(followId, sender.actorId, actorUri)
            val undo = FollowActivities.undo("${sender.actorId}/undo/${UUID.randomUUID()}", follow)
            queue.cancelPendingFollowAndEnqueueUndo(accountId, inbox, followId, AppJson.encodeToString(OutgoingActivity.serializer(), undo), clock.instant())
        }
        span.setAttribute(FollowingSpan.ENQUEUED, true)
        onEnqueued()
    }
}
