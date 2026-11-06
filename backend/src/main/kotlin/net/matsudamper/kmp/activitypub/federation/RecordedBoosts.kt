package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.boost.BoostStore

internal class RecordedBoosts(
    private val boostRepository: BoostRepository,
    private val receivedNotes: ReceivedRemoteNotes,
) : BoostStore {
    override fun add(boost: BoostStore.ReceivedBoost): Boolean {
        return boostRepository.add(
            notePublicId = PublicNoteId(boost.notePublicId.value),
            actor = boost.actor.toRecord(),
            activityUri = boost.activityUri,
            receivedAt = boost.receivedAt,
        )
    }

    override suspend fun saveRemote(boost: BoostStore.ReceivedRemoteBoost) {
        receivedNotes.saveBoost(boost)
    }

    override fun remove(
        actorUri: String,
        activityUri: String,
    ): Boolean = boostRepository.remove(actorUri = actorUri, activityUri = activityUri)

    override fun removeActor(actorUri: String): Int = boostRepository.removeActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = boostRepository.findPublicKeyPem(actorUri)
}
