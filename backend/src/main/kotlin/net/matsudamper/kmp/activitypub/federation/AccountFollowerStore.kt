package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kmp.activitypub.logic.NotificationRecorder
import net.matsudamper.kmp.activitypub.logic.ServerHost
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.NotificationActorRef
import net.matsudamper.kmp.activitypub.repository.NotificationEvent
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.actor.RemoteActorProfile
import net.matsudamper.kotpub.follower.FollowerStore

/**
 * @param onEnqueued Accept か Reject を積んだ後に呼ぶ。相手の画面ではフォローが保留のまま待っているので、次の巡回を待たずに送らせる
 * @param maxDeliveryTargetsPerHost 同じホストからのフォローで配る先の上限。判定のたびに読み直す
 */
internal class AccountFollowerStore(
    private val followerRepository: FollowerRepository,
    private val blocks: InboxBlocks,
    private val notifications: NotificationRecorder,
    private val onEnqueued: () -> Unit,
    private val maxDeliveryTargetsPerHost: () -> Int,
) : FollowerStore {
    override fun record(
        username: String,
        follower: RemoteActor,
        followActivityUri: String,
        receivedAt: Instant,
        acceptBody: String,
    ): Boolean {
        val recorded = followerRepository.record(
            username = username,
            follower = RemoteActorRecord(
                actorUri = follower.actorId,
                inbox = follower.inbox,
                sharedInbox = follower.sharedInbox,
                publicKeyPem = follower.publicKeyPem,
                profile = follower.profile.toRecord(),
            ),
            followerHost = ServerHost.ofUri(follower.actorId),
            followActivityUri = followActivityUri,
            receivedAt = receivedAt,
            acceptBody = acceptBody,
        )
        if (recorded) {
            onEnqueued()
            notifications.record(
                NotificationEvent.Followed(
                    followeeUsername = username,
                    actor = NotificationActorRef.Remote(follower.actorId),
                    occurredAt = receivedAt,
                ),
            )
        }
        return recorded
    }

    override fun refuses(username: String, follower: RemoteActor): Boolean =
        blocks.blocks(username, follower.actorId) || exceedsDeliveryTargetLimit(follower)

    override fun reject(
        username: String,
        follower: RemoteActor,
        followActivityUri: String,
        receivedAt: Instant,
        rejectBody: String,
    ): Boolean {
        val rejected = followerRepository.reject(
            username = username,
            followerActorUri = follower.actorId,
            followActivityUri = followActivityUri,
            rejectsUnrecordedFollow = true,
            inbox = follower.inbox,
            rejectBody = rejectBody,
            now = receivedAt,
        )
        if (rejected) onEnqueued()
        return rejected
    }

    override fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean = followerRepository.remove(
        username = username,
        followerActorUri = followerActorUri,
        followActivityUri = followActivityUri,
    )

    override fun findFolloweeUsername(
        followerActorUri: String,
        followActivityUri: String,
    ): String? = followerRepository.findFolloweeUsername(
        followerActorUri = followerActorUri,
        followActivityUri = followActivityUri,
    )

    override fun removeAccount(username: String): Int = followerRepository.removeAccount(username)

    override fun removeRemoteActor(actorUri: String): Int = followerRepository.removeRemoteActor(actorUri, Instant.now())

    override fun findPublicKeyPem(actorUri: String): String? = followerRepository.findPublicKeyPem(actorUri)

    override fun rememberPublicKeyPem(
        actorUri: String,
        publicKeyPem: String,
    ) {
        followerRepository.updatePublicKeyPem(actorUri = actorUri, publicKeyPem = publicKeyPem)
    }

    override fun rememberProfile(
        actorUri: String,
        profile: RemoteActorProfile,
    ) {
        followerRepository.updateProfile(actorUri = actorUri, profile = profile.toRecord())
    }

    override fun list(
        username: String,
        after: String?,
        limit: Int,
    ): List<String> = followerRepository.listAcceptedFollowerActorUris(username = username, after = after, limit = limit)

    override fun count(username: String): Long = followerRepository.countAcceptedFollowers(username)

    override fun deliveryTargets(username: String): List<String> = followerRepository.findDeliveryInboxes(username)

    /**
     * 受け付けると同じホストからのフォローの配る先が上限を超えるか。
     *
     * sharedInbox を持たず inbox を 1 人ずつ分けた偽アクターを並べると、投稿 1 件ごとの配信がその人数分に増える。
     * 鍵付きのアクターはいくらでも作れるので件数で止める。アクター文書を置くホストは相手が用意する必要があるので、ホストごとに数える。
     * 配信を受け持つのはこのサーバーなので、こちらのアカウントは問わずにまとめて数える
     *
     * 受け付けると相手の配る先は置き換わるので、置き換えた後の数で比べる。記録済みの相手が自分だけの inbox を移しただけなら数は増えない。
     * ここで断ると、記録済みのフォローまで外れる
     */
    private fun exceedsDeliveryTargetLimit(follower: RemoteActor): Boolean {
        val host = ServerHost.ofUri(follower.actorId) ?: return false
        val othersDeliveryInboxes = followerRepository.findDeliveryInboxesOfOtherFollowersOnHost(
            followerActorUri = follower.actorId,
            host = host,
        )
        val deliveryInbox = follower.sharedInbox ?: follower.inbox
        return (othersDeliveryInboxes + deliveryInbox).size > maxDeliveryTargetsPerHost()
    }

    private fun RemoteActorProfile.toRecord(): RemoteActorProfileRecord =
        RemoteActorProfileRecord(
            preferredUsername = preferredUsername,
            displayName = displayName,
            profileUrl = profileUrl,
            iconUrl = iconUrl,
            headerUrl = headerUrl,
            emojis = emojis.toRecords(),
            summary = summary,
            fields = fields.toFieldRecords(),
        )
}
