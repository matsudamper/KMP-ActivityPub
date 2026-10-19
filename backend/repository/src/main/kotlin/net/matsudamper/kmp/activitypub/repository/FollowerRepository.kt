package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * 他のサーバーからこちらのアカウントへのフォロー。
 *
 * Follow を受けた時点ではまだ成立しておらず、Accept を相手に送れたときに成立する。
 * 成立していないフォローは一覧・件数・配信先に出さない
 */
interface FollowerRepository {
    /**
     * フォローを記録して、Accept を送るものとして積む。
     * 同じ相手から受け直したら行は増やさず、まだ送れていない Accept を置き換える
     *
     * @return 宛先のアカウントが無ければ false
     */
    fun record(
        username: String,
        follower: RemoteActorRecord,
        followActivityUri: String,
        receivedAt: Instant,
        acceptBody: String,
    ): Boolean

    /**
     * @param followActivityUri null なら元の Follow の id を問わない
     */
    fun remove(
        username: String,
        followerActorUri: String,
        followActivityUri: String?,
    ): Boolean

    fun findFolloweeUsername(
        followerActorUri: String,
        followActivityUri: String,
    ): String?

    fun removeAccount(username: String): Int

    /**
     * @return 外れたフォローの数
     */
    fun removeRemoteActor(actorUri: String): Int

    fun findPublicKeyPem(actorUri: String): String?

    fun updatePublicKeyPem(
        actorUri: String,
        publicKeyPem: String,
    )

    fun updateProfile(
        actorUri: String,
        profile: RemoteActorProfileRecord,
    )

    /**
     * 成立したフォローの相手のアクター URL を URL 順に返す
     *
     * @param after この URL より後ろを返す。null なら先頭から
     */
    fun listAcceptedFollowerActorUris(
        username: String,
        after: String?,
        limit: Int,
    ): List<String>

    fun countAcceptedFollowers(username: String): Long

    /**
     * 成立したフォローの相手の inbox。sharedInbox があればそちらにまとめる
     */
    fun findDeliveryInboxes(username: String): List<String>

    fun findDueAcceptDeliveries(
        now: Instant,
        limit: Int,
    ): List<PendingFollowAccept>

    /**
     * Accept を送れたのでフォローを成立させる
     */
    fun completeAcceptDelivery(
        delivery: PendingFollowAccept,
        acceptedAt: Instant,
    )

    fun rescheduleAcceptDelivery(
        delivery: PendingFollowAccept,
        nextAttemptAt: Instant,
    )

    /**
     * Accept を送れないまま諦めるので、フォローごと消す
     */
    fun abandonAcceptDelivery(delivery: PendingFollowAccept)
}

data class RemoteActorRecord(
    val actorUri: String,
    val inbox: String,
    val sharedInbox: String?,
    val publicKeyPem: String,
    val profile: RemoteActorProfileRecord,
)

data class RemoteActorProfileRecord(
    val preferredUsername: String?,
    val displayName: String?,
    val profileUrl: String?,
    val iconUrl: String?,
)

/**
 * @param attempts これまでに送ろうとして失敗した回数
 */
data class PendingFollowAccept(
    val id: Long,
    val followeeId: AccountId,
    val followeeUsername: String,
    val remoteActorId: Long,
    val inbox: String,
    val body: String,
    val attempts: Int,
)
