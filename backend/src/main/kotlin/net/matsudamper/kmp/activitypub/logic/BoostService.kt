package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.util.UUID
import net.matsudamper.kmp.activitypub.repository.AccountBoostRepository
import net.matsudamper.kmp.activitypub.repository.BoostTarget
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.OutgoingBoost
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.activity.AnnounceActivity
import net.matsudamper.kotpub.activity.BoostActivities
import net.matsudamper.kotpub.activity.BoostAudience
import net.matsudamper.kotpub.activity.OutgoingActivity
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.json.AppJson
import net.matsudamper.kotpub.note.NoteUrls

/**
 * ブーストを記録して、フォロワーと投稿者への Announce / Undo を積む。送るのは配信キューのワーカー
 *
 * @param onEnqueued 積んだ後に呼ぶ。次の巡回を待たずに送らせる
 * @param onBoosted ブーストしたアカウントのタイムラインに、ブーストを載せる
 */
class BoostService(
    private val directory: ActorDirectory,
    private val accountService: AccountService,
    private val boosts: AccountBoostRepository,
    private val followerRepository: FollowerRepository,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
    private val onBoosted: (TimelinePosition) -> Unit,
) {
    /**
     * @param postId タイムラインの投稿の id
     * @return 外すときは、ブーストしていない投稿でも [SetBoostedResult.Updated]
     */
    fun setBoosted(accountId: AccountId?, postId: String, boosted: Boolean): SetBoostedResult {
        if (accountId == null) return SetBoostedResult.NotLoggedIn
        val account = accountService.account(accountId) ?: return SetBoostedResult.NotLoggedIn
        val sender = directory.resolve(account.username) ?: return SetBoostedResult.NotLoggedIn
        if (!boosted) {
            // 外すときは今読めるかを問わない。投稿者のフォローを外した後でも、記録したブーストの宛先へ Undo を届ける
            if (boosts.unboostAndEnqueueUndo(accountId = accountId, member = postId, now = clock.instant())) onEnqueued()
            return SetBoostedResult.Updated
        }
        val target = boosts.target(accountId, postId) ?: return SetBoostedResult.PostNotFound
        val boostedNote = boostedNoteOf(sender, target) ?: return SetBoostedResult.PostNotFound
        val announce = BoostActivities.announce(
            id = "${sender.actorId}/announces/${UUID.randomUUID()}",
            actor = sender.actorId,
            objectUri = boostedNote.objectUri,
            objectAuthor = boostedNote.authorActorUri,
            followers = sender.followers,
            published = clock.instant(),
            audience = if (boostedNote.unlisted) BoostAudience.UNLISTED else BoostAudience.PUBLIC,
        )
        val undo = BoostActivities.undo(id = "${sender.actorId}/undo/${UUID.randomUUID()}", announce = announce)
        val position = boosts.boostAndEnqueue(
            OutgoingBoost(
                accountId = accountId,
                member = postId,
                announceId = announce.id,
                announceBody = AppJson.encodeToString(AnnounceActivity.serializer(), announce),
                undoBody = AppJson.encodeToString(OutgoingActivity.serializer(), undo),
                inboxes = (followerRepository.findDeliveryInboxes(sender.username) + listOfNotNull(boostedNote.authorInbox)).distinct(),
                objectUri = boostedNote.objectUri,
                objectAuthor = boostedNote.authorActorUri,
                unlisted = boostedNote.unlisted,
                boostedAt = clock.instant(),
            ),
        )
        if (position != null) {
            onEnqueued()
            onBoosted(position)
        }
        return SetBoostedResult.Updated
    }

    /**
     * 投稿者が消した他のサーバーの投稿を消し、その投稿へのこのサーバーのアカウントのブーストを外して Undo を積む。
     * 投稿者の Delete はこちらのフォロワーのサーバーに届くとは限らず、届かないと消えた投稿のブーストが残る
     */
    fun deleteRemoteNote(actorUri: String, objectUri: String) {
        val removed = boosts.deleteRemoteNoteAndUndoBoosts(actorUri = actorUri, objectUri = objectUri, now = clock.instant())
        if (removed > 0) onEnqueued()
    }

    /**
     * @return このサーバーの投稿の投稿者が見つからなければ null
     */
    private fun boostedNoteOf(sender: ActorUrls, target: BoostTarget): BoostedNote? = when (target) {
        is BoostTarget.Remote -> BoostedNote(
            objectUri = target.objectUri,
            authorActorUri = target.authorActorUri,
            // 投稿者がフォロワーでなくても、ブーストされたことが届くように投稿者にも送る
            authorInbox = target.authorInbox,
            unlisted = target.unlisted,
        )

        is BoostTarget.Local -> directory.resolve(target.authorUsername)?.let { author ->
            BoostedNote(
                objectUri = NoteUrls(domain = sender.domain, publicId = PublicNoteId(target.publicId.value)).noteUrl,
                authorActorUri = author.actorId,
                // 投稿者はこのサーバーのアカウントなので、配らなくても分かる
                authorInbox = null,
                unlisted = false,
            )
        }
    }

    private class BoostedNote(
        val objectUri: String,
        val authorActorUri: String,
        val authorInbox: String?,
        val unlisted: Boolean,
    )
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
