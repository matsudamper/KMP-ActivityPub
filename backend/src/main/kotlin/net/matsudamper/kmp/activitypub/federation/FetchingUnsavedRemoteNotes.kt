package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.UnsavedRemoteNotes
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.note.RemoteNoteLookup
import net.matsudamper.kotpub.note.RemoteNotes
import org.slf4j.LoggerFactory

internal class FetchingUnsavedRemoteNotes(
    private val timeline: TimelineRepository,
    private val remoteNotes: RemoteNotes,
    private val receivedNotes: ReceivedRemoteNotes,
) : UnsavedRemoteNotes {
    private val logger = LoggerFactory.getLogger(FetchingUnsavedRemoteNotes::class.java)

    override fun storedMember(viewer: AccountId, objectUri: String): String? =
        timeline.storedRemotePosts(viewer, setOf(objectUri))
            .firstOrNull { (it.noteRef as? TimelineNoteRef.Remote)?.objectUri == objectUri }
            ?.reactionMember

    override suspend fun import(viewer: AccountId, objectUri: String): String? =
        when (val lookup = remoteNotes.find(objectUri)) {
            is RemoteNoteLookup.Found -> receivedNotes.storeForReaction(viewer, lookup.note)

            RemoteNoteLookup.NotFound, RemoteNoteLookup.Unavailable -> {
                logger.warn("いいねやブーストを付ける投稿を取れなかった: $objectUri result=$lookup")
                null
            }
        }
}
