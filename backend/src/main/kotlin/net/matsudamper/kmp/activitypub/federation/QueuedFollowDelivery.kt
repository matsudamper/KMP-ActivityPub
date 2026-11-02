package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import java.util.UUID
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.FollowDelivery
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
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
) : FollowDelivery {
    private val logger = LoggerFactory.getLogger(QueuedFollowDelivery::class.java)

    override suspend fun send(accountId: AccountId, actorUri: String, followed: Boolean, followId: String?) {
        val account = checkNotNull(accounts.account(accountId))
        val sender = checkNotNull(directory.resolve(account.username))
        if (followed) {
            val remote = checkNotNull(actors.findActor(actorUri)) { "相手の inbox を取得できない" }
            storedActors.save(remote.toRecord(), clock.instant())
            if (actorUri != remote.actorId) storedActors.remove(actorUri)
            if (!following.contains(accountId, actorUri)) return
            val canonicalFollowingAdded = actorUri != remote.actorId && following.add(accountId, remote.actorId, clock.instant())
            if (actorUri != remote.actorId) {
                following.remove(accountId, actorUri)
                if (!canonicalFollowingAdded) return
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
            if (!enqueued) return
        } else {
            val inbox = storedActors.findByUri(actorUri)?.inbox
            if (inbox == null || followId == null) {
                logger.warn("保存済みの inbox または Follow の ID が無いので Undo を諦める: $actorUri")
                return
            }
            val follow = FollowActivities.follow(followId, sender.actorId, actorUri)
            val undo = FollowActivities.undo("${sender.actorId}/undo/${UUID.randomUUID()}", follow)
            queue.cancelPendingFollowAndEnqueueUndo(accountId, inbox, followId, AppJson.encodeToString(OutgoingActivity.serializer(), undo), clock.instant())
        }
        onEnqueued()
    }
}
