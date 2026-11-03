package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId

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

data class TimelinePost(
    val position: TimelinePosition,
    val contentHtml: String,
    val publishedAt: Instant,
    val actorUri: String?,
    val username: String,
    val displayName: String?,
    val summary: String?,
    val sensitive: Boolean,
)

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
)
