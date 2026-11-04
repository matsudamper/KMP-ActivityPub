package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.StampCount
import net.matsudamper.kmp.activitypub.repository.StampRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

internal class FakeStampRepository : StampRepository {
    private val stamps = mutableMapOf<Triple<PublicNoteId, String, String>, Pair<RemoteActorRecord, String?>>()

    override fun put(
        notePublicId: PublicNoteId,
        actor: RemoteActorRecord,
        emoji: String,
        emojiImageUrl: String?,
        receivedAt: Instant,
    ): Boolean {
        stamps[Triple(notePublicId, actor.actorUri, emoji)] = actor to emojiImageUrl
        return true
    }

    override fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = stamps.remove(Triple(notePublicId, actorUri, emoji)) != null

    override fun removeActor(actorUri: String): Int {
        val keys = stamps.keys.filter { it.second == actorUri }
        keys.forEach { stamps.remove(it) }
        return keys.size
    }

    override fun findPublicKeyPem(actorUri: String): String? =
        stamps.entries.firstOrNull { it.key.second == actorUri }?.value?.first?.publicKeyPem

    override fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, List<StampCount>> =
        notePublicIds.associateWith { id ->
            stamps.entries
                .filter { it.key.first == id }
                .groupingBy { it.key.third to it.value.second }
                .eachCount()
                .map { (key, count) -> StampCount(emoji = key.first, emojiImageUrl = key.second, count = count.toLong()) }
                .sortedByDescending { it.count }
        }
}
