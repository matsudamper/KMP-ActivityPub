package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kmp.activitypub.logic.RemoteUser
import net.matsudamper.kmp.activitypub.logic.RemoteUserLookup
import net.matsudamper.kmp.activitypub.logic.RemoteUserProfile
import net.matsudamper.kmp.activitypub.logic.RemoteUserProfiles
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.note.HttpRemoteOutbox
import org.jsoup.Jsoup
import org.jsoup.safety.Safelist

internal class KotpubRemoteUserProfiles(
    private val users: RemoteUserLookup,
    private val actors: RemoteActors,
    private val outbox: HttpRemoteOutbox,
) : RemoteUserProfiles {
    override suspend fun find(username: String, host: String): RemoteUserProfile? {
        val user = users.findByAcct(username, host) ?: return null
        return profileFor(user)
    }

    override suspend fun findByActorUri(actorUri: String): RemoteUserProfile? {
        val user = users.findByUrl(actorUri) ?: return null
        return profileFor(user)
    }

    override suspend fun recentNotes(actorUri: String): List<RemoteNoteInput> {
        val fetchedAt = Instant.now()
        return outbox.recent(actorUri, limit = 20).map {
            RemoteNoteInput(
                objectUri = it.id,
                actorUri = actorUri,
                contentHtml = Jsoup.clean(it.contentHtml, Safelist.basic()),
                visibility = if (it.unlisted) "unlisted" else "public",
                localRecipients = setOf(),
                inReplyTo = null,
                summary = it.summary?.let { summary -> Jsoup.clean(summary, Safelist.none()) },
                sensitive = it.sensitive,
                publishedAt = Instant.parse(it.publishedAt),
                receivedAt = fetchedAt,
                images = it.attachments.mapNotNull { attachment -> attachment.toImage() },
                emojis = it.emojis.toRecords(),
            )
        }
    }

    private suspend fun profileFor(user: RemoteUser): RemoteUserProfile? {
        val actor = actors.findActor(user.actorUri) ?: return null
        return RemoteUserProfile(user = user, headerUrl = actor.profile.headerUrl, biography = actor.profile.summary)
    }
}
