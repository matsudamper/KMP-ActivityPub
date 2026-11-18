package net.matsudamper.kmp.activitypub.federation

import java.net.URI
import java.time.Clock
import java.time.Instant
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import net.matsudamper.kmp.activitypub.logic.NotificationRecorder
import net.matsudamper.kmp.activitypub.repository.NotificationActorRef
import net.matsudamper.kmp.activitypub.repository.NotificationEvent
import net.matsudamper.kmp.activitypub.repository.NotificationPostRef
import net.matsudamper.kmp.activitypub.repository.PollOptionRecord
import net.matsudamper.kmp.activitypub.repository.PollRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteBoostInput
import net.matsudamper.kmp.activitypub.repository.RemoteMediaKind
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.inOpenTelemetrySuspendSpan
import net.matsudamper.kmp.activitypub.timeline.TimelineService
import net.matsudamper.kmp.activitypub.timeline.TimelineSpan
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActorLookup
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.boost.BoostStore.ReceivedRemoteBoost
import net.matsudamper.kotpub.note.NoteVisibility
import net.matsudamper.kotpub.note.ReceivedNote
import net.matsudamper.kotpub.note.ReceivedNoteAttachment
import net.matsudamper.kotpub.note.ReceivedPoll
import net.matsudamper.kotpub.note.RemoteNoteStore
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

internal class ReceivedRemoteNotes(
    private val repository: TimelineRepository,
    private val actors: RemoteActors,
    private val storedActors: RemoteActorRepository,
    private val directory: ActorDirectory,
    private val blocks: InboxBlocks,
    private val timeline: TimelineService,
    private val notifications: NotificationRecorder,
    private val clock: Clock,
    private val deleteRemoteNote: (actorUri: String, objectUri: String) -> List<String>,
    openTelemetry: OpenTelemetry = OpenTelemetryInitializer.get(),
) : RemoteNoteStore {
    private val tracer = openTelemetry.getTracer("activitypub-inbox")

    override suspend fun save(note: ReceivedNote) {
        inOpenTelemetrySuspendSpan(tracer, "Inbox.Create", false) { span -> saveInSpan(note, span) }
    }

    override suspend fun update(note: ReceivedNote) {
        // 宛先は変えないので、このサーバーの宛先は引かない
        val revised = note.toRemoteNoteInput(localRecipients = setOf(), now = clock.instant())
        timeline.publishUpdated(repository.reviseRemote(revised))
    }

    override suspend fun delete(actorId: String, objectId: String) {
        timeline.publishDeleted(deleteRemoteNote(actorId, objectId))
    }

    /**
     * いいねやブーストを付ける投稿を、ホームタイムラインに配らずに保存する
     *
     * @return 保存した投稿の、いいねやブーストに渡すタイムラインの member。投稿者を記録できないか、保存できない投稿なら null
     */
    suspend fun storeForReaction(viewer: AccountId, note: ReceivedNote): String? {
        val now = clock.instant()
        if (storeActorIfMissing(note.attributedTo, now) != null) return null
        return repository.storeFetchedNotes(viewer, listOf(note.toRemoteNoteInput(localRecipients = setOf(), now = now)))[note.id]?.reactionMember
    }

    suspend fun saveBoost(boost: ReceivedRemoteBoost) {
        inOpenTelemetrySuspendSpan(tracer, "Inbox.Announce", false) { span -> saveBoostInSpan(boost, span) }
    }

    private suspend fun saveBoostInSpan(boost: ReceivedRemoteBoost, span: Span) {
        span.setAttribute(InboxSpan.OBJECT_URI, boost.note.id)
        span.setAttribute(InboxSpan.ACTOR, boost.actorUri)
        span.setAttribute(InboxSpan.SAVED, false)
        span.setAttribute(InboxSpan.OUTCOME, "discarded")
        val interest = repository.receptionInterest(boost.actorUri, setOf())
        span.setAttribute(InboxSpan.FOLLOWING_ACCOUNT_COUNT, interest.followingAccountCount)
        if (!interest.shouldReceive) {
            span.setAttribute(InboxSpan.DISCARD_REASON, "booster_not_followed")
            return
        }
        val now = clock.instant()
        val boosterReason = storeActorIfMissing(boost.actorUri, now)
        if (boosterReason != null) {
            span.setAttribute(InboxSpan.DISCARD_REASON, "booster_$boosterReason")
            return
        }
        val authorReason = storeActorIfMissing(boost.note.attributedTo, now)
        if (authorReason != null) {
            span.setAttribute(InboxSpan.DISCARD_REASON, "author_$authorReason")
            return
        }
        val position = repository.receiveBoost(
            RemoteBoostInput(
                activityUri = boost.activityUri,
                boosterUri = boost.actorUri,
                boostedAt = boost.published ?: now,
                receivedAt = now,
                note = boost.note.toRemoteNoteInput(localRecipients = setOf(), now = now),
            ),
        )
        if (position == null) {
            span.setAttribute(InboxSpan.DISCARD_REASON, "duplicate_or_not_public")
            return
        }
        span.setAttribute(InboxSpan.SAVED, true)
        span.setAttribute(InboxSpan.OUTCOME, "saved")
        span.setAttribute(TimelineSpan.MEMBER, position.member)
        timeline.enqueue(position)
    }

    /**
     * 宛先だけを指定した投稿 (DM) は、Mention があっても通知しない
     *
     * @param recipients 宛先に入っている、このサーバーのアカウントのうちブロックしていないもの
     */
    private fun recordMentions(note: ReceivedNote, recipients: Set<String>, now: Instant) {
        if (note.visibility == NoteVisibility.Direct) return
        localUsernamesOf(note.mentions).filter { it in recipients }.forEach { username ->
            notifications.record(
                NotificationEvent.Mentioned(
                    mentionedUsername = username,
                    post = NotificationPostRef.Remote(note.id),
                    actor = NotificationActorRef.Remote(note.attributedTo),
                    occurredAt = now,
                ),
            )
        }
    }

    private fun localUsernamesOf(actorIds: List<String>): List<String> = actorIds.mapNotNull { target ->
        val uri = runCatching { URI(target) }.getOrNull() ?: return@mapNotNull null
        val username = uri.path?.substringAfterLast('/') ?: return@mapNotNull null
        directory.resolve(username)?.takeIf { it.actorId == target }?.username
    }

    /**
     * @return 記録できなかった理由。記録済みか記録できたら null
     */
    private suspend fun storeActorIfMissing(actorUri: String, now: Instant): String? {
        if (storedActors.findByUri(actorUri) != null) return null
        val actor = when (val lookup = actors.findActor(actorUri)) {
            is RemoteActorLookup.Found -> lookup.actor
            RemoteActorLookup.Gone, is RemoteActorLookup.Unavailable -> return "actor_not_found"
        }
        if (actor.actorId != actorUri) return "actor_id_mismatch"
        storedActors.save(actor.toRecord(), now)
        return null
    }

    private suspend fun saveInSpan(note: ReceivedNote, span: Span) {
        span.setAttribute(InboxSpan.OBJECT_URI, note.id)
        span.setAttribute(InboxSpan.ACTOR, note.attributedTo)
        span.setAttribute(InboxSpan.SAVED, false)
        span.setAttribute(InboxSpan.OUTCOME, "discarded")
        val recipients = localUsernamesOf(note.to + note.cc).filterNot { blocks.blocks(it, note.attributedTo) }.toSet()
        val interest = repository.receptionInterest(note.attributedTo, recipients)
        span.setAttribute(InboxSpan.FOLLOWING_ACCOUNT_COUNT, interest.followingAccountCount)
        span.setAttribute(InboxSpan.LOCAL_RECIPIENT_COUNT, interest.localRecipientCount)
        if (!interest.shouldReceive) {
            span.setAttribute(InboxSpan.DISCARD_REASON, "not_followed_and_not_addressed")
            return
        }
        val now = clock.instant()
        val actorReason = storeActorIfMissing(note.attributedTo, now)
        if (actorReason != null) {
            span.setAttribute(InboxSpan.DISCARD_REASON, actorReason)
            return
        }
        val position = repository.receive(note.toRemoteNoteInput(localRecipients = recipients, now = now))
        if (position != null) {
            span.setAttribute(InboxSpan.SAVED, true)
            span.setAttribute(InboxSpan.OUTCOME, "saved")
            span.setAttribute(TimelineSpan.MEMBER, position.member)
            timeline.enqueue(position)
            recordMentions(note, recipients, now)
        } else {
            val reason = if (repository.hasRemoteNote(note.id)) "duplicate" else "no_eligible_recipient_or_actor"
            span.setAttribute(InboxSpan.DISCARD_REASON, reason)
        }
    }
}

internal fun ReceivedNote.toRemoteNoteInput(localRecipients: Set<String>, now: Instant): RemoteNoteInput = RemoteNoteInput(
    objectUri = id,
    actorUri = attributedTo,
    contentHtml = Jsoup.clean(contentWithoutQuoteFallback(), Safelist.basic()),
    language = language,
    visibility = visibility.name.lowercase(),
    localRecipients = localRecipients,
    inReplyTo = inReplyTo,
    quoteUri = quote,
    summary = summary?.let { Jsoup.clean(it, Safelist.none()) },
    sensitive = sensitive,
    publishedAt = published ?: now,
    receivedAt = now,
    editedAt = updated,
    images = attachments.mapNotNull { it.toRemoteMedia() },
    emojis = emojis.toRecords(),
    poll = poll?.toRecord(),
)

private fun ReceivedPoll.toRecord(): PollRecord = PollRecord(
    options = options.map { option -> PollOptionRecord(name = Jsoup.clean(option.name, Safelist.none()), votesCount = option.votesCount?.toLong()) },
    multipleChoice = multipleChoice,
    endsAt = endsAt,
    votersCount = votersCount?.toLong(),
)

/**
 * 引用を読めない相手向けに、Mastodon や Misskey は本文の末尾に `RE: 引用先の URL` を quote-inline のクラスで足す。
 * 引用先は別に出すので、引用のときは落とす
 */
private fun ReceivedNote.contentWithoutQuoteFallback(): String {
    if (quote == null) return content
    val body = Jsoup.parseBodyFragment(content).body()
    body.select(".quote-inline").remove()
    return body.html()
}

// 音声などの出せない添付は落とす。申告が画像か動画のものだけを残す
internal fun ReceivedNoteAttachment.toRemoteMedia(): TimelinePostImage.Remote? {
    val mediaKind =
        when {
            mediaType?.startsWith("image/", ignoreCase = true) == true -> RemoteMediaKind.Image
            mediaType?.startsWith("video/", ignoreCase = true) == true -> RemoteMediaKind.Video
            else -> return null
        }
    return TimelinePostImage.Remote(
        url = url,
        description = name?.let { Jsoup.clean(it, Safelist.none()) }?.takeIf { it.isNotBlank() },
        mediaKind = mediaKind,
    )
}
