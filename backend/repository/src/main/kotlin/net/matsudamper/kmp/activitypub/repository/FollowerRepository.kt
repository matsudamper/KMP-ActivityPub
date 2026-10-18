package net.matsudamper.kmp.activitypub.repository

import java.time.Instant

/**
 * 他のサーバーのアクターから受けたフォロー。
 *
 * `Accept` を送れるまではフォローが成立していないので、一覧・件数・配信先には出さない
 */
interface FollowerRepository {
    /**
     * フォローを記録して、送る `Accept` を積む。同じ相手からの記録があれば行は増やさず、積んである `Accept` は置き換える
     *
     * @return 宛先のアカウントがいなければ false
     */
    fun record(
        username: String,
        follower: RemoteActorRecord,
        followActivityUri: String,
        receivedAt: Instant,
        acceptBody: String,
        acceptNextAttemptAt: Instant,
    ): Boolean

    /**
     * @param followActivityUri null なら元の `Follow` の id を問わない
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
     * @return 消えたフォローの数
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
     * @param after このアクター URL より後ろを返す
     */
    fun listAccepted(
        username: String,
        after: String?,
        limit: Int,
    ): List<String>

    fun countAccepted(username: String): Long

    /**
     * `sharedInbox` があればそちらにまとめる
     */
    fun deliveryTargets(username: String): List<String>

    fun findDueAccepts(
        now: Instant,
        limit: Int,
    ): List<PendingFollowAccept>

    /**
     * `Accept` を送れたので、フォローを成立させる
     */
    fun markAccepted(
        accept: PendingFollowAccept,
        acceptedAt: Instant,
    )

    fun postponeAccept(
        accept: PendingFollowAccept,
        nextAttemptAt: Instant,
    )

    /**
     * 送るのを諦める。成立していないフォローも一緒に消す。相手が `Follow` を送り直せばまた記録される
     */
    fun giveUpAccept(accept: PendingFollowAccept)
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
 * 送り直しの間に置き換えられた `Accept` を送れたものとして扱わないよう、[body] も行の特定に使う
 */
data class PendingFollowAccept(
    val followeeUsername: String,
    val followerActorUri: String,
    val followerInbox: String,
    val body: String,
    val attempts: Int,
)
