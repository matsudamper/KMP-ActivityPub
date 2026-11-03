package net.matsudamper.kmp.activitypub.federation

import java.net.URI
import java.time.Clock
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.inOpenTelemetrySuspendSpan
import net.matsudamper.kmp.activitypub.timeline.HomeTimelineService
import net.matsudamper.kmp.activitypub.timeline.TimelineSpan
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActors
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
    private val timeline: HomeTimelineService,
    private val clock: Clock,
    openTelemetry: OpenTelemetry = OpenTelemetryInitializer.get(),
) : RemoteNoteStore {
    private val tracer = openTelemetry.getTracer("activitypub-inbox")

    override suspend fun save(note: ReceivedNote) {
        inOpenTelemetrySuspendSpan(tracer, "Inbox.Create", false) { span -> saveInSpan(note, span) }
    }

    override suspend fun delete(actorId: String, objectId: String) {
        repository.deleteRemote(actorId, objectId)
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
        if (storedActors.findByUri(note.attributedTo) == null) {
            val actor = actors.findActor(note.attributedTo)
            if (actor == null) {
                span.setAttribute(InboxSpan.DISCARD_REASON, "actor_not_found")
                return
            }
            if (actor.actorId != note.attributedTo) {
                span.setAttribute(InboxSpan.DISCARD_REASON, "actor_id_mismatch")
                return
            }
            storedActors.save(actor.toRecord(), now)
        }
        val position = repository.receive(
            RemoteNoteInput(
                objectUri = note.id,
                actorUri = note.attributedTo,
                contentHtml = Jsoup.clean(note.content, Safelist.basic()),
                visibility = note.visibility.name.lowercase(),
                localRecipients = recipients,
                inReplyTo = note.inReplyTo,
                summary = note.summary?.let { Jsoup.clean(it, Safelist.none()) },
                sensitive = note.sensitive,
                publishedAt = note.published ?: now,
                receivedAt = now,
                images = note.attachments.mapNotNull { it.toImage() },
            ),
        )
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

    // 動画や音声も添付で来る。画像だけを出すので、申告が画像のものだけを残す
    private fun ReceivedNoteAttachment.toImage(): TimelinePostImage? {
        if (mediaType?.startsWith("image/") != true) return null
        return TimelinePostImage(url = url, description = name?.let { Jsoup.clean(it, Safelist.none()) }?.takeIf { it.isNotBlank() })
    }
}
