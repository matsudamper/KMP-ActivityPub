package net.matsudamper.kmp.activitypub.federation

import java.net.URI
import java.time.Clock
import net.matsudamper.kmp.activitypub.logic.RemoteNoteFetchResult
import net.matsudamper.kmp.activitypub.logic.RemoteUser
import net.matsudamper.kmp.activitypub.logic.UnsavedRemoteNotes
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.actor.RemoteActors
import net.matsudamper.kotpub.note.ReceivedNote
import net.matsudamper.kotpub.note.RemoteNoteLookup
import net.matsudamper.kotpub.note.RemoteNotes
import org.slf4j.LoggerFactory

internal class FetchingUnsavedRemoteNotes(
    private val timeline: TimelineRepository,
    private val remoteNotes: RemoteNotes,
    private val receivedNotes: ReceivedRemoteNotes,
    private val remoteActors: RemoteActors,
    private val storedActors: RemoteActorRepository,
    private val clock: Clock,
) : UnsavedRemoteNotes {
    private val logger = LoggerFactory.getLogger(FetchingUnsavedRemoteNotes::class.java)

    override fun storedMember(viewer: AccountId, objectUri: String): String? =
        timeline.storedRemotePosts(viewer, setOf(objectUri))
            .firstOrNull { (it.noteRef as? TimelineNoteRef.Remote)?.objectUri == objectUri }
            ?.reactionMember

    override suspend fun import(viewer: AccountId, objectUri: String): String? =
        when (val lookup = remoteNotes.find(objectUri)) {
            is RemoteNoteLookup.Found -> receivedNotes.storeForReaction(viewer, lookup.note)

            is RemoteNoteLookup.NotFound, is RemoteNoteLookup.Unavailable -> {
                logger.warn("いいねやブーストを付ける投稿を取れなかった: $objectUri result=$lookup")
                null
            }
        }

    override suspend fun fetch(objectUri: String): RemoteNoteFetchResult {
        val note = when (val lookup = remoteNotes.find(objectUri)) {
            is RemoteNoteLookup.Found -> lookup.note

            RemoteNoteLookup.NotFound -> return RemoteNoteFetchResult.NotFound

            RemoteNoteLookup.Unavailable -> {
                logger.warn("投稿を取れなかった: $objectUri")
                return RemoteNoteFetchResult.Unavailable
            }
        }
        val input = note.toRemoteNoteInput(localRecipients = setOf(), now = clock.instant())
        // 保存するときと同じく、誰でも読める投稿で、投稿者と同じサーバーにあるものだけを出す
        if (input.visibility !in PUBLISHED_VISIBILITIES || !note.isOnAuthorsOrigin()) return RemoteNoteFetchResult.NotFound
        // アクターが取れないのは、相手のサーバーが応えなかったときと区別できないので、取り直せるようにする
        val author = findAuthor(note.attributedTo) ?: return RemoteNoteFetchResult.Unavailable
        return RemoteNoteFetchResult.Found(note = input, author = author)
    }

    private suspend fun findAuthor(actorUri: String): RemoteUser? {
        val stored = storedActors.findByUri(actorUri)
        if (stored != null) {
            return RemoteUser(
                actorUri = stored.actorUri,
                username = stored.profile.preferredUsername ?: stored.actorUri,
                displayName = stored.profile.displayName,
                iconUrl = stored.profile.iconUrl,
                displayNameEmojis = stored.profile.emojis,
            )
        }
        val actor = remoteActors.findActor(actorUri) ?: return null
        if (actor.actorId != actorUri) return null
        return RemoteUser(
            actorUri = actor.actorId,
            username = actor.profile.preferredUsername ?: actor.actorId,
            displayName = actor.profile.displayName,
            iconUrl = actor.profile.iconUrl,
            displayNameEmojis = actor.profile.emojis.toRecords(),
        )
    }

    private fun ReceivedNote.isOnAuthorsOrigin(): Boolean {
        val noteUri = runCatching { URI(id) }.getOrNull() ?: return false
        val authorUri = runCatching { URI(attributedTo) }.getOrNull() ?: return false
        return noteUri.scheme == authorUri.scheme && noteUri.host == authorUri.host && noteUri.port == authorUri.port
    }

    private companion object {
        val PUBLISHED_VISIBILITIES = setOf("public", "unlisted")
    }
}
