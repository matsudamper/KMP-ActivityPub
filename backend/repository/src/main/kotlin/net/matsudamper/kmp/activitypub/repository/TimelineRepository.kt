package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

interface TimelineRepository {
    fun receptionInterest(actorUri: String, localRecipients: Set<String>): RemoteNoteReceptionInterest
    fun shouldReceive(actorUri: String, localRecipients: Set<String>): Boolean = receptionInterest(actorUri, localRecipients).shouldReceive
    fun receive(note: RemoteNoteInput): TimelinePosition?

    /**
     * ブーストした相手をフォローしているアカウントが居なければ記録しない。
     * ブーストされた投稿が未保存なら一緒に保存する
     *
     * @return タイムラインに載せる位置。記録しなかったか、記録済みなら null
     */
    fun receiveBoost(boost: RemoteBoostInput): TimelinePosition?
    fun hasRemoteNote(objectUri: String): Boolean

    /**
     * 投稿者が消した投稿に印を付ける。タイムラインに削除済みと出すので行は残す
     *
     * @return 削除済みになったタイムラインの member。投稿そのものと、それをブーストした行。保存していないか、既に消されていれば空
     */
    fun deleteRemote(actorUri: String, objectUri: String, deletedAt: Instant): List<String>

    /**
     * [member] を載せるアカウントを、アカウントの id 順に [after] より後ろから返す
     */
    fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<TimelineRecipient>
    fun touch(accountId: AccountId, now: Instant, updateIntervalSeconds: Long)

    /**
     * @param groupId 読む購読先。null ならメイン
     */
    fun rebuild(accountId: AccountId, groupId: FollowingGroupId?, limit: Int): List<TimelinePosition>

    /**
     * @param groupId 読む購読先。null ならメイン
     */
    fun page(accountId: AccountId, groupId: FollowingGroupId?, after: TimelinePosition?, limit: Int): List<TimelinePosition>
    fun hydrate(accountId: AccountId, members: List<String>): List<TimelinePost>

    /**
     * [accountId] が読める、[actorUri] の保存済みの投稿と [actorUri] のブーストを新しい順に返す
     */
    fun actorPosts(accountId: AccountId, actorUri: String, limit: Int): List<TimelinePost>
    fun localPosition(publicId: String): TimelinePosition?

    /**
     * [booster] が付けたブーストを新しい順に返す。ブーストした時点で読めていたので、[viewer] が今読めるかは問わない
     *
     * @param viewer いいねやブーストの有無を見るアカウント。ログインしていなければ null
     */
    fun accountBoosts(booster: AccountId, viewer: AccountId?, limit: Int): List<TimelinePost>

    /**
     * フォローしていない相手のプロフィールで取った投稿を、いいねやブーストできるように保存する。タイムラインには載せない。
     * 公開と、公開タイムラインに載せない公開の投稿だけを保存する。投稿の id が投稿者と別のホストにあるものは保存しない。投稿者のアクターは記録済みであること
     *
     * @param viewer いいねやブーストの有無を見るアカウント
     * @return 投稿の id ごとの、保存した投稿。保存できなかった投稿は含まない
     */
    fun storeFetchedNotes(viewer: AccountId, notes: List<RemoteNoteInput>): Map<String, TimelinePost>
}

data class RemoteNoteReceptionInterest(val followingAccountCount: Long, val localRecipientCount: Long) {
    val shouldReceive: Boolean get() = followingAccountCount > 0 || localRecipientCount > 0
}

data class TimelinePosition(val score: Long, val member: String)

/**
 * @param groupId 投稿を載せる購読先。メインなら null
 */
data class TimelineRecipient(val accountId: AccountId, val groupId: FollowingGroupId?)

/**
 * @param actorIconUrl 他のサーバーの投稿者のプロフィール画像の URL。このサーバーの投稿や、画像の無い相手は null
 * @param actorUpdatedAt 他のサーバーの投稿者の情報を最後に受け取った時刻。このサーバーの投稿は null
 * @param favouriteCount 他のサーバーから届いたお気に入りと、このサーバーのアカウントのいいねを合わせた数。他のサーバーの投稿の数は受け取らないので null
 * @param stamps 他のサーバーから届いたスタンプ。他のサーバーの投稿は受け取らないので空
 * @param likedByMe 読んでいるアカウントがいいねしているか。ブーストで載った投稿は、ブーストされた元の投稿へのいいね
 * @param boostedByMe 読んでいるアカウントがブーストしているか。ブーストできない投稿（フォロワー限定など、公開でも公開タイムラインに載せないものでもない投稿）は null。
 *   ブーストで載った投稿は、ブーストされた元の投稿のブースト
 * @param reactionMember いいねやブーストを付け外しするときに指す member。ブーストで載った投稿は、ブーストされた元の投稿を指す。
 *   受け取ったブーストが取り消されても同じ投稿を指し続ける
 * @param noteRef 投稿を外から指すための元。URL はこのサーバーのドメインで決まるので、ここでは作らない
 * @param boostCount 他のサーバーから届いたブーストの数。他のサーバーの投稿の数は受け取らないので null
 * @param emojis 本文と注意書きに入るカスタム絵文字。このサーバーの投稿は空
 * @param actorEmojis 他のサーバーの投稿者の表示名に入るカスタム絵文字。このサーバーの投稿は空
 * @param boostedBy フォローしている相手か自分のブーストとして載った投稿なら、ブーストした相手。それ以外は null。
 *   [position] はブーストの位置で、本文や投稿者はブーストされた投稿のもの
 * @param deleted 投稿者が投稿を消したか、ブーストした相手がブーストを取り消したか。ほかの項目は消される前のまま
 */
data class TimelinePost(
    val position: TimelinePosition,
    val contentHtml: String,
    val publishedAt: Instant,
    val actorUri: String?,
    val username: String,
    val displayName: String?,
    val summary: String?,
    val sensitive: Boolean,
    val actorIconUrl: String?,
    val actorUpdatedAt: Instant?,
    val images: List<TimelinePostImage>,
    val emojis: List<CustomEmojiRecord>,
    val actorEmojis: List<CustomEmojiRecord>,
    val favouriteCount: Long?,
    val stamps: List<StampCount>,
    val likedByMe: Boolean,
    val boostedByMe: Boolean?,
    val reactionMember: String,
    val noteRef: TimelineNoteRef,
    val boostCount: Long?,
    val boostedBy: TimelineBooster?,
    val deleted: Boolean,
) {
    /**
     * このサーバーの投稿の公開 id。他のサーバーの投稿は null
     */
    val localPublicId: PublicNoteId? get() = (noteRef as? TimelineNoteRef.Local)?.publicId
}

sealed interface TimelineNoteRef {
    data class Local(val publicId: PublicNoteId) : TimelineNoteRef

    data class Remote(val objectUri: String) : TimelineNoteRef
}

/**
 * @param actorUri ブーストした他のサーバーのアクター。このサーバーのアカウントのブーストは null
 * @param username 相手の preferredUsername。無ければアクターの URI
 * @param iconUrl 他のサーバーのアクターのプロフィール画像の URL。このサーバーのアカウントは null
 * @param updatedAt 他のサーバーのアクターの情報を最後に受け取った時刻。このサーバーのアカウントは null
 */
data class TimelineBooster(
    val actorUri: String?,
    val username: String,
    val displayName: String?,
    val iconUrl: String?,
    val emojis: List<CustomEmojiRecord>,
    val updatedAt: Instant?,
)

sealed interface TimelinePostImage {
    /**
     * 画像の説明文。無ければ null
     */
    val description: String?

    /**
     * @param url 他のサーバーにある画像の URL
     */
    data class Remote(val url: String, override val description: String?) : TimelinePostImage

    /**
     * このサーバーの投稿の画像。URL はこのサーバーのドメインで決まるので、識別子だけ持つ
     */
    data class Local(val publicId: NoteImagePublicId, override val description: String?) : TimelinePostImage
}

data class RemoteNoteInput(
    val objectUri: String,
    val actorUri: String,
    val contentHtml: String,
    val visibility: String,
    val localRecipients: Set<String>,
    val inReplyTo: String?,
    val summary: String?,
    val sensitive: Boolean,
    val publishedAt: Instant,
    val receivedAt: Instant,
    val images: List<TimelinePostImage.Remote>,
    val emojis: List<CustomEmojiRecord>,
)

/**
 * @param boostedAt `Announce` の日時。無ければ受け取った時刻
 * @param note ブーストされた投稿。投稿者のアクターは記録済みであること
 */
data class RemoteBoostInput(
    val activityUri: String,
    val boosterUri: String,
    val boostedAt: Instant,
    val receivedAt: Instant,
    val note: RemoteNoteInput,
)
