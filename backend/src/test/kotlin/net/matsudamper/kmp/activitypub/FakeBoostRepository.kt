package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.BoostRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

internal class FakeBoostRepository : BoostRepository {
    private val boosts = mutableMapOf<Pair<PublicNoteId, String>, Pair<RemoteActorRecord, String>>()

    override fun add(
        notePublicId: PublicNoteId,
        actor: RemoteActorRecord,
        activityUri: String,
        receivedAt: Instant,
    ): Boolean = boosts.putIfAbsent(notePublicId to actor.actorUri, actor to activityUri) == null

    override fun remove(
        actorUri: String,
        activityUri: String,
    ): Boolean = boosts.entries.removeAll { it.key.second == actorUri && it.value.second == activityUri }

    override fun removeActor(actorUri: String): Int {
        val keys = boosts.keys.filter { it.second == actorUri }
        keys.forEach { boosts.remove(it) }
        return keys.size
    }

    override fun findPublicKeyPem(actorUri: String): String? =
        boosts.entries.firstOrNull { it.key.second == actorUri }?.value?.first?.publicKeyPem

    override fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Long> =
        notePublicIds.associateWith { id -> boosts.keys.count { it.first == id }.toLong() }
}
