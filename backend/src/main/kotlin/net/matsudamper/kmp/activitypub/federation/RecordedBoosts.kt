package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import net.matsudamper.kmp.activitypub.logic.NotificationRecorder
import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.repository.NotificationActorRef
import net.matsudamper.kmp.activitypub.repository.NotificationEvent
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.timeline.TimelineService
import net.matsudamper.kotpub.boost.BoostStore

internal class RecordedBoosts(
    private val boostRepository: BoostRepository,
    private val receivedNotes: ReceivedRemoteNotes,
    private val notifications: NotificationRecorder,
    private val blocks: InboxBlocks,
    private val timeline: TimelineService,
    private val clock: Clock,
) : BoostStore {
    override fun add(boost: BoostStore.ReceivedBoost): Boolean {
        val notePublicId = PublicNoteId(boost.notePublicId.value)
        if (blocks.blocksOnNote(notePublicId, boost.actor.actorId)) return false
        val added = boostRepository.add(
            notePublicId = notePublicId,
            actor = boost.actor.toRecord(),
            activityUri = boost.activityUri,
            receivedAt = boost.receivedAt,
        )
        if (added) {
            notifications.record(
                NotificationEvent.Boosted(
                    notePublicId = notePublicId,
                    actor = NotificationActorRef.Remote(boost.actor.actorId),
                    occurredAt = boost.receivedAt,
                ),
            )
        }
        return added
    }

    override suspend fun saveRemote(boost: BoostStore.ReceivedRemoteBoost) {
        receivedNotes.saveBoost(boost)
    }

    override fun remove(
        actorUri: String,
        activityUri: String,
    ): Boolean {
        val removal = boostRepository.remove(actorUri = actorUri, activityUri = activityUri, undoneAt = clock.instant()) ?: return false
        timeline.publishBoostUndone(listOfNotNull(removal.undoneTimelineMember))
        return true
    }

    override fun removeActor(actorUri: String): Int = boostRepository.removeActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = boostRepository.findPublicKeyPem(actorUri)
}
