package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

internal class FakeFavouriteRepository : FavouriteRepository {
    private val favourites = mutableMapOf<Pair<PublicNoteId, String>, RemoteActorRecord>()
    private val earlyUndone = mutableMapOf<Pair<String, String>, Instant>()

    override fun add(
        notePublicId: PublicNoteId,
        actor: RemoteActorRecord,
        receivedAt: Instant,
    ): Boolean = favourites.putIfAbsent(notePublicId to actor.actorUri, actor) == null

    override fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
    ): Boolean = favourites.remove(notePublicId to actorUri) != null

    override fun removeActor(actorUri: String): Int {
        val keys = favourites.keys.filter { it.second == actorUri }
        keys.forEach { favourites.remove(it) }
        return keys.size
    }

    override fun findPublicKeyPem(actorUri: String): String? =
        favourites.entries.firstOrNull { it.key.second == actorUri }?.value?.publicKeyPem

    override fun counts(notePublicIds: Set<PublicNoteId>): Map<PublicNoteId, Long> =
        notePublicIds.associateWith { id -> favourites.keys.count { it.first == id }.toLong() }

    override fun rememberEarlyUndone(
        actorUri: String,
        activityUri: String,
        expiresAt: Instant,
    ) {
        earlyUndone[actorUri to activityUri] = expiresAt
    }

    override fun isEarlyUndone(
        actorUri: String,
        activityUri: String,
        now: Instant,
    ): Boolean = earlyUndone[actorUri to activityUri]?.isAfter(now) == true
}
