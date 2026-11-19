package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import java.util.UUID
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.BlockDelivery
import net.matsudamper.kmp.activitypub.logic.BlockLists
import net.matsudamper.kmp.activitypub.repository.ActorBlockRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.activity.BlockActivities
import net.matsudamper.kotpub.activity.FollowActivities
import net.matsudamper.kotpub.activity.OutgoingActivity
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActorLookup
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.json.AppJson
import org.slf4j.LoggerFactory

internal class QueuedBlockDelivery(
    private val accounts: AccountService,
    private val directory: ActorDirectory,
    private val actors: RemoteActors,
    private val storedActors: RemoteActorRepository,
    private val actorBlocks: ActorBlockRepository,
    private val followers: FollowerRepository,
    private val blockLists: BlockLists,
    private val clock: Clock,
    private val wakeUpQueue: () -> Unit,
) : BlockDelivery {
    private val logger = LoggerFactory.getLogger(QueuedBlockDelivery::class.java)

    override suspend fun blockActor(accountId: AccountId, actorUri: String) {
        rejectBlockedFollowers(accountId)
        if (actorBlocks.findBlockActivityId(accountId, actorUri) != null) return
        val senderActorId = senderActorId(accountId) ?: return
        val inbox = findInbox(actorUri) ?: return
        val blockActivityId = "$senderActorId#blocks/${UUID.randomUUID()}"
        val block = BlockActivities.block(blockActivityId, senderActorId, actorUri)
        val recorded = actorBlocks.recordBlockActivity(
            accountId = accountId,
            actorUri = actorUri,
            blockActivityId = blockActivityId,
            inbox = inbox,
            body = AppJson.encodeToString(OutgoingActivity.serializer(), block),
            now = clock.instant(),
        )
        if (recorded) wakeUpQueue()
    }

    override fun rejectBlockedFollowers(accountId: AccountId) {
        val account = accounts.account(accountId) ?: return
        val senderActorId = directory.resolve(account.username)?.actorId ?: return
        val blockList = blockLists.of(accountId)
        val blockedFollows = followers.listFollowRecords(account.username).filter { blockList.hidesActor(it.followerActorUri) }
        blockedFollows.forEach { record ->
            val follow = FollowActivities.follow(record.followActivityUri, record.followerActorUri, senderActorId)
            val reject = FollowActivities.reject("$senderActorId#rejects/follows/${UUID.randomUUID()}", senderActorId, follow)
            followers.reject(
                username = account.username,
                followerActorUri = record.followerActorUri,
                inbox = record.inbox,
                rejectBody = AppJson.encodeToString(OutgoingActivity.serializer(), reject),
                now = clock.instant(),
            )
        }
        if (blockedFollows.isNotEmpty()) wakeUpQueue()
    }

    override fun undoBlockBody(accountId: AccountId, actorUri: String, blockActivityId: String): String? {
        val senderActorId = senderActorId(accountId) ?: return null
        val block = BlockActivities.block(blockActivityId, senderActorId, actorUri)
        val undo = BlockActivities.undo("$senderActorId#undo/${UUID.randomUUID()}", block)
        return AppJson.encodeToString(OutgoingActivity.serializer(), undo)
    }

    override fun onEnqueued() {
        wakeUpQueue()
    }

    private fun senderActorId(accountId: AccountId): String? {
        val account = accounts.account(accountId) ?: return null
        return directory.resolve(account.username)?.actorId
    }

    private suspend fun findInbox(actorUri: String): String? {
        val stored = storedActors.findByUri(actorUri)
        if (stored != null) return stored.inbox
        return when (val lookup = actors.findActor(actorUri)) {
            is RemoteActorLookup.Found -> lookup.actor.inbox

            RemoteActorLookup.Gone, is RemoteActorLookup.Unavailable -> {
                logger.warn("相手の inbox を取得できないので Block を送れない: $actorUri $lookup")
                null
            }
        }
    }
}
