package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kmp.activitypub.logic.OutboxPosition
import net.matsudamper.kmp.activitypub.logic.RemoteNotesPage
import net.matsudamper.kmp.activitypub.logic.RemoteUserProfile
import net.matsudamper.kmp.activitypub.logic.RemoteUserProfiles
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kotpub.note.HttpRemoteOutbox
import net.matsudamper.kotpub.note.RemoteOutboxCursor
import net.matsudamper.kotpub.note.RemoteOutboxNote
import net.matsudamper.kotpub.note.RemoteOutboxPageResult
import net.matsudamper.kotpub.note.RemoteOutboxResult
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist
import org.slf4j.LoggerFactory

internal class KotpubRemoteUserProfiles(
    private val users: KotpubRemoteUserLookup,
    private val outbox: HttpRemoteOutbox,
) : RemoteUserProfiles {
    private val logger = LoggerFactory.getLogger(KotpubRemoteUserProfiles::class.java)

    override suspend fun find(username: String, host: String): RemoteUserProfile? = users.findWithActorByAcct(username, host)?.toProfile()

    override suspend fun findByActorUri(actorUri: String): RemoteUserProfile? = users.findWithActorByUrl(actorUri)?.toProfile()

    override suspend fun recentNotesPage(actorUri: String, from: OutboxPosition?): RemoteNotesPage? =
        when (val result = outbox.recentPage(actorUri, from?.let { RemoteOutboxCursor(pageId = it.pageId, itemOffset = it.itemOffset) })) {
            is RemoteOutboxPageResult.Found -> RemoteNotesPage(
                notes = result.page.notes.map { it.toInput(actorUri, Instant.now()) },
                next = result.page.next?.let { OutboxPosition(pageId = it.pageId, itemOffset = it.itemOffset) },
            )

            RemoteOutboxPageResult.Unavailable -> {
                logger.warn("outbox を取れなかった: $actorUri from=$from")
                null
            }
        }

    override suspend fun featuredNotes(actorUri: String): List<RemoteNoteInput> =
        outbox.featured(actorUri, limit = 20).notesOrEmpty(actorUri, kind = "featured")

    private fun RemoteOutboxResult.notesOrEmpty(actorUri: String, kind: String): List<RemoteNoteInput> =
        when (this) {
            is RemoteOutboxResult.Found -> notes.map { it.toInput(actorUri, Instant.now()) }

            RemoteOutboxResult.Unavailable -> {
                logger.warn("$kind を取れなかった: $actorUri")
                listOf()
            }
        }

    private fun RemoteOutboxNote.toInput(actorUri: String, fetchedAt: Instant): RemoteNoteInput = RemoteNoteInput(
        objectUri = id,
        actorUri = actorUri,
        contentHtml = Jsoup.clean(contentHtml, Safelist.basic()),
        visibility = if (unlisted) "unlisted" else "public",
        localRecipients = setOf(),
        inReplyTo = null,
        summary = summary?.let { Jsoup.clean(it, Safelist.none()) },
        sensitive = sensitive,
        publishedAt = Instant.parse(publishedAt),
        receivedAt = fetchedAt,
        images = attachments.mapNotNull { attachment -> attachment.toImage() },
        emojis = emojis.toRecords(),
    )

    private fun FoundRemoteUser.toProfile(): RemoteUserProfile =
        RemoteUserProfile(user = user, headerUrl = actor.profile.headerUrl, biography = actor.profile.summary)
}
