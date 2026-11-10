package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.util.UUID
import net.matsudamper.kmp.activitypub.repository.AccountBoostRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.OutgoingBoost
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.activity.AnnounceActivity
import net.matsudamper.kotpub.activity.BoostActivities
import net.matsudamper.kotpub.activity.OutgoingActivity
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.json.AppJson

/**
 * 他のサーバーの投稿のブーストを記録して、フォロワーと投稿者への Announce / Undo を積む。送るのは配信キューのワーカー
 *
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 */
class BoostService(
    private val directory: ActorDirectory,
    private val accountService: AccountService,
    private val boosts: AccountBoostRepository,
    private val followerRepository: FollowerRepository,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
) {
    /**
     * @param postId タイムラインの投稿の id
     */
    fun setBoosted(accountId: AccountId?, postId: String, boosted: Boolean): SetBoostedResult {
        if (accountId == null) return SetBoostedResult.NotLoggedIn
        val account = accountService.account(accountId) ?: return SetBoostedResult.NotLoggedIn
        val sender = directory.resolve(account.username) ?: return SetBoostedResult.NotLoggedIn
        val target = boosts.target(accountId, postId) ?: return SetBoostedResult.PostNotFound
        // 投稿者がフォロワーでなくても、ブーストされたことが届くように投稿者にも送る
        val inboxes = (followerRepository.findDeliveryInboxes(sender.username) + target.authorInbox).distinct()
        val enqueued = if (boosted) {
            val announce = BoostActivities.announce(
                id = "${sender.actorId}/announces/${UUID.randomUUID()}",
                actor = sender.actorId,
                objectUri = target.objectUri,
                objectAuthor = target.authorActorUri,
                followers = sender.followers,
                published = clock.instant(),
            )
            boosts.boostAndEnqueue(
                OutgoingBoost(
                    accountId = accountId,
                    member = postId,
                    announceId = announce.id,
                    inboxes = inboxes,
                    body = AppJson.encodeToString(AnnounceActivity.serializer(), announce),
                    boostedAt = clock.instant(),
                ),
            )
        } else {
            boosts.unboostAndEnqueueUndo(accountId = accountId, member = postId, inboxes = inboxes, now = clock.instant()) { announceBody ->
                val announce = AppJson.decodeFromString(AnnounceActivity.serializer(), announceBody)
                val undo = BoostActivities.undo(id = "${sender.actorId}/undo/${UUID.randomUUID()}", announce = announce)
                AppJson.encodeToString(OutgoingActivity.serializer(), undo)
            }
        }
        if (enqueued) onEnqueued()
        return SetBoostedResult.Updated
    }
}

sealed interface SetBoostedResult {
    /**
     * 既に望んだ状態だったときも含む
     */
    data object Updated : SetBoostedResult

    data object NotLoggedIn : SetBoostedResult

    /**
     * 無いか、読めないか、ブーストできない投稿
     */
    data object PostNotFound : SetBoostedResult
}
