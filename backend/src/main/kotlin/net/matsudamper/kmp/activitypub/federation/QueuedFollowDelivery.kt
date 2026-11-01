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
            val stored = storedActors.findByUri(actorUri)
            val remote = if (stored == null) actors.findActor(actorUri) else null
            if (remote != null) {
                require(remote.actorId == actorUri)
                storedActors.save(remote.toRecord(), clock.instant())
            }
            val inbox = stored?.inbox ?: checkNotNull(remote?.inbox) { "相手の inbox を取得できない" }
            val id = "${sender.actorId}/follows/${UUID.randomUUID()}"
            following.recordActivity(accountId, actorUri, id)
            val follow = FollowActivities.follow(id, sender.actorId, actorUri)
            queue.enqueueFollow(accountId, inbox, AppJson.encodeToString(OutgoingActivity.serializer(), follow), clock.instant())
        } else {
            val inbox = storedActors.findByUri(actorUri)?.inbox
            if (inbox == null || followId == null) {
                logger.warn("保存済みの inbox または Follow の ID が無いので Undo を諦める: $actorUri")
                return
            }
            val follow = FollowActivities.follow(followId, sender.actorId, actorUri)
            val undo = FollowActivities.undo("${sender.actorId}/undo/${UUID.randomUUID()}", follow)
            queue.enqueueUndo(accountId, inbox, AppJson.encodeToString(OutgoingActivity.serializer(), undo), clock.instant())
        }
        onEnqueued()
    }
}
