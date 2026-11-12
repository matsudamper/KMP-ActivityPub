package net.matsudamper.kmp.activitypub.federation

import java.net.URI
import java.time.Clock
import java.time.Instant
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteBoostInput
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.inOpenTelemetrySuspendSpan
import net.matsudamper.kmp.activitypub.timeline.TimelineService
import net.matsudamper.kmp.activitypub.timeline.TimelineSpan
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.boost.BoostStore.ReceivedRemoteBoost
import net.matsudamper.kotpub.note.ReceivedNote
import net.matsudamper.kotpub.note.ReceivedNoteAttachment
import net.matsudamper.kotpub.note.RemoteNoteStore
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

internal class ReceivedRemoteNotes(
    private val repository: TimelineRepository,
    private val actors: RemoteActors,
    private val storedActors: RemoteActorRepository,
    private val directory: ActorDirectory,
    private val timeline: TimelineService,
    private val clock: Clock,
    private val deleteRemoteNote: (actorUri: String, objectUri: String) -> List<String>,
    openTelemetry: OpenTelemetry = OpenTelemetryInitializer.get(),
) : RemoteNoteStore {
    private val tracer = openTelemetry.getTracer("activitypub-inbox")

    override suspend fun save(note: ReceivedNote) {
        inOpenTelemetrySuspendSpan(tracer, "Inbox.Create", false) { span -> saveInSpan(note, span) }
    }

    override suspend fun delete(actorId: String, objectId: String) {
        timeline.publishDeleted(deleteRemoteNote(actorId, objectId))
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
                note = boost.note.toInput(localRecipients = setOf(), now = now),
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
     * @return 記録できなかった理由。記録済みか記録できたら null
     */
    private suspend fun storeActorIfMissing(actorUri: String, now: Instant): String? {
        if (storedActors.findByUri(actorUri) != null) return null
        val actor = actors.findActor(actorUri) ?: return "actor_not_found"
        if (actor.actorId != actorUri) return "actor_id_mismatch"
        storedActors.save(actor.toRecord(), now)
        return null
    }

    private suspend fun saveInSpan(note: ReceivedNote, span: Span) {
        span.setAttribute(InboxSpan.OBJECT_URI, note.id)
        span.setAttribute(InboxSpan.ACTOR, note.attributedTo)
        span.setAttribute(InboxSpan.SAVED, false)
        span.setAttribute(InboxSpan.OUTCOME, "discarded")
        val recipients = (note.to + note.cc).mapNotNull { target ->
            val uri = runCatching { URI(target) }.getOrNull() ?: return@mapNotNull null
            val username = uri.path?.substringAfterLast('/') ?: return@mapNotNull null
            directory.resolve(username)?.takeIf { it.actorId == target }?.username
        }.toSet()
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
        val position = repository.receive(note.toInput(localRecipients = recipients, now = now))
        if (position != null) {
            span.setAttribute(InboxSpan.SAVED, true)
            span.setAttribute(InboxSpan.OUTCOME, "saved")
            span.setAttribute(TimelineSpan.MEMBER, position.member)
            timeline.enqueue(position)
        } else {
            val reason = if (repository.hasRemoteNote(note.id)) "duplicate" else "no_eligible_recipient_or_actor"
            span.setAttribute(InboxSpan.DISCARD_REASON, reason)
        }
    }

    private fun ReceivedNote.toInput(localRecipients: Set<String>, now: Instant): RemoteNoteInput = RemoteNoteInput(
        objectUri = id,
        actorUri = attributedTo,
        contentHtml = Jsoup.clean(content, Safelist.basic()),
        visibility = visibility.name.lowercase(),
        localRecipients = localRecipients,
        inReplyTo = inReplyTo,
        summary = summary?.let { Jsoup.clean(it, Safelist.none()) },
        sensitive = sensitive,
        publishedAt = published ?: now,
        receivedAt = now,
        images = attachments.mapNotNull { it.toImage() },
        emojis = emojis.toRecords(),
    )
}

// 動画や音声も添付で来る。画像だけを出すので、申告が画像のものだけを残す
internal fun ReceivedNoteAttachment.toImage(): TimelinePostImage.Remote? {
    if (mediaType?.startsWith("image/") != true) return null
    return TimelinePostImage.Remote(url = url, description = name?.let { Jsoup.clean(it, Safelist.none()) }?.takeIf { it.isNotBlank() })
}
