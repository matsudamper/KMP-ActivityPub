package net.matsudamper.kmp.activitypub.federation

import java.net.URI
import java.time.Clock
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.timeline.HomeTimelineService
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.note.ReceivedNote
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
) : RemoteNoteStore {
    override suspend fun save(note: ReceivedNote) {
        val recipients = (note.to + note.cc).mapNotNull { target ->
            val uri = runCatching { URI(target) }.getOrNull() ?: return@mapNotNull null
            val username = uri.path?.substringAfterLast('/') ?: return@mapNotNull null
            directory.resolve(username)?.takeIf { it.actorId == target }?.username
        }.toSet()
        if (!repository.shouldReceive(note.attributedTo, recipients)) return
        val now = clock.instant()
        if (storedActors.findByUri(note.attributedTo) == null) {
            val actor = actors.findActor(note.attributedTo) ?: return
            if (actor.actorId != note.attributedTo) return
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
            ),
        )
        if (position != null) timeline.enqueue(position)
    }

    override suspend fun delete(actorId: String, objectId: String) {
        repository.deleteRemote(actorId, objectId)
    }
}
