package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId

interface TimelineRepository {
    fun receptionInterest(actorUri: String, localRecipients: Set<String>): RemoteNoteReceptionInterest
    fun shouldReceive(actorUri: String, localRecipients: Set<String>): Boolean = receptionInterest(actorUri, localRecipients).shouldReceive
    fun receive(note: RemoteNoteInput): TimelinePosition?
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
