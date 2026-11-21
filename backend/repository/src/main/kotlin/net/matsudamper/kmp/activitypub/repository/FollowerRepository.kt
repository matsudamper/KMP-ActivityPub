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
     * 宛先のアカウントが相手のアクターかサーバーをブロックしていれば記録しない。
     * ブロックの確認と記録を分けると、間にブロックされた相手のフォローが成立する
     *
     * @param followerHost 相手のアクター URL の小文字のホスト。サーバーのブロックと照らし合わせる
     * @return 宛先のアカウントが無いか、ブロックしている相手なら false
     */
    fun record(
        username: String,
        follower: RemoteActorRecord,
        followerHost: String?,
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

    /**
     * フォローの記録を消して、Reject を積む。まだ送れていない Accept も一緒に消える。
     * 消すのと投函を分けると、こちらではフォロワーでないのに相手はフォローしているつもりのまま残る
     *
     * @param followActivityUri 断る Follow の id。この id のフォローだけを外す。読んでから外すまでに同じ相手が Follow し直していても、
     *   新しいフォローは外さない
     * @param rejectsUnrecordedFollow true なら、記録が無くても Reject を積む。受けたばかりの Follow を断るときに使う。
     *   false なら、記録を外せたときだけ積む
     * @return 宛先のアカウントが無いか、Reject を積まなかったら false
     */
    fun reject(
        username: String,
        followerActorUri: String,
        followActivityUri: String,
        rejectsUnrecordedFollow: Boolean,
        inbox: String,
        rejectBody: String,
        now: Instant,
    ): Boolean

    /**
     * フォローしてきた相手を、Accept を送れていないものも含めて返す
     */
    fun listFollowRecords(username: String): List<FollowRecord>

    fun findFolloweeUsername(
        followerActorUri: String,
        followActivityUri: String,
    ): String?

    fun removeAccount(username: String): Int

    /**
     * 消えた相手のアクターと、その相手の投稿を消す。相手の投稿へのこのサーバーのアカウントのブーストも外し、Announce を届けた宛先に Undo を積む。
     * 相手の削除がこちらのフォロワーのサーバーに届くとは限らないので、ブーストを届けた宛先にはこちらから取り消しを送る
     *
     * @param now Undo を積む時刻
     * @return 外れたフォローの数
     */
    fun removeRemoteActor(actorUri: String, now: Instant): Int

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

    /**
     * [host] のアクターからのフォローで配る先の inbox。[followerActorUri] 自身の分は含めない。
     * こちらのどのアカウントへのフォローかは問わず、Accept を送れる前のものも含める
     *
     * Follow を受け付けると相手の配る先は置き換わるので、置き換えた後の配る先の数はここに新しい配る先を足したものになる
     *
     * @param host 小文字のホスト
     */
    fun findDeliveryInboxesOfOtherFollowersOnHost(
        followerActorUri: String,
        host: String,
    ): Set<String>
}

/**
 * @param followActivityUri 相手が送ってきた Follow の id
 * @param inbox 相手個人の inbox
 */
data class FollowRecord(
    val followerActorUri: String,
    val followActivityUri: String,
    val inbox: String,
)

data class RemoteActorRecord(
    val actorUri: String,
    val inbox: String,
    val sharedInbox: String?,
    val publicKeyPem: String,
    val profile: RemoteActorProfileRecord,
)

/**
 * @param summary 自己紹介。相手が書いた HTML のまま
 * @param fields プロフィールの補足情報。相手が並べた順
 */
data class RemoteActorProfileRecord(
    val preferredUsername: String?,
    val displayName: String?,
    val profileUrl: String?,
    val iconUrl: String?,
    val headerUrl: String?,
    val emojis: List<CustomEmojiRecord>,
    val summary: String?,
    val fields: List<RemoteActorProfileFieldRecord>,
)

/**
 * @param htmlValue 相手が書いた HTML のまま
 */
data class RemoteActorProfileFieldRecord(
    val name: String,
    val htmlValue: String,
)
