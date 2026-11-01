package net.matsudamper.kmp.activitypub.repository

import java.time.Instant

/**
 * 他のサーバーからこちらのアカウントへのフォロー。
 *
 * Follow を受けた時点ではまだ成立しておらず、Accept を相手に送れたときに成立する。
 * 成立していないフォローは一覧・件数・配信先に出さない
 */
interface FollowerRepository {
    /**
     * フォローを記録して、Accept を配信キューに積む。
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

    /**
     * 成立したフォローの相手を、成立した新しい順に [after] より後ろから [limit] 件まで返す
     */
    fun listAcceptedFollowers(
        username: String,
        after: ActorListPosition?,
        limit: Int,
    ): List<ListedActor>

    fun countAcceptedFollowers(username: String): Long

    /**
     * 成立したフォローの相手の inbox。sharedInbox があればそちらにまとめる
     */
    fun findDeliveryInboxes(username: String): List<String>
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
