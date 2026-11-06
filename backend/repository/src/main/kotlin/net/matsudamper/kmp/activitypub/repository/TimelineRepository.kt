package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
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
    fun deleteRemote(actorUri: String, objectUri: String)
    fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<AccountId>
    fun touch(accountId: AccountId, now: Instant, updateIntervalSeconds: Long)
    fun rebuild(accountId: AccountId, limit: Int): List<TimelinePosition>
    fun page(accountId: AccountId, after: TimelinePosition?, limit: Int): List<TimelinePosition>
    fun hydrate(accountId: AccountId, members: List<String>): List<TimelinePost>

    /**
     * [accountId] が読める、[actorUri] の保存済みの投稿を新しい順に返す
     */
    fun actorPosts(accountId: AccountId, actorUri: String, limit: Int): List<TimelinePost>
    fun localPosition(publicId: String): TimelinePosition?
}

data class RemoteNoteReceptionInterest(val followingAccountCount: Long, val localRecipientCount: Long) {
    val shouldReceive: Boolean get() = followingAccountCount > 0 || localRecipientCount > 0
}

data class TimelinePosition(val score: Long, val member: String)

/**
 * @param actorIconUrl 他のサーバーの投稿者のプロフィール画像の URL。このサーバーの投稿や、画像の無い相手は null
 * @param actorUpdatedAt 他のサーバーの投稿者の情報を最後に受け取った時刻。このサーバーの投稿は null
 * @param favouriteCount 他のサーバーから届いたお気に入りと、このサーバーのアカウントのいいねを合わせた数。他のサーバーの投稿の数は受け取らないので null
 * @param stamps 他のサーバーから届いたスタンプ。他のサーバーの投稿は受け取らないので空
 * @param likedByMe 読んでいるアカウントがいいねしているか。ブーストで載った投稿はいいねできないので null
 * @param noteRef 投稿を外から指すための元。URL はこのサーバーのドメインで決まるので、ここでは作らない
 * @param boostCount 他のサーバーから届いたブーストの数。他のサーバーの投稿の数は受け取らないので null
 * @param boostedBy フォローしている相手のブーストとして載った投稿なら、ブーストした相手。それ以外は null。
 *   [position] はブーストの位置で、本文や投稿者はブーストされた投稿のもの
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
    val favouriteCount: Long?,
    val stamps: List<StampCount>,
    val likedByMe: Boolean?,
    val noteRef: TimelineNoteRef,
    val boostCount: Long?,
    val boostedBy: TimelineBooster?,
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
 * @param username 相手の preferredUsername。無ければアクターの URI
 * @param iconUrl プロフィール画像の URL。無ければ null
 * @param updatedAt 相手の情報を最後に受け取った時刻
 */
data class TimelineBooster(
    val actorUri: String,
    val username: String,
    val displayName: String?,
    val iconUrl: String?,
    val updatedAt: Instant,
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
