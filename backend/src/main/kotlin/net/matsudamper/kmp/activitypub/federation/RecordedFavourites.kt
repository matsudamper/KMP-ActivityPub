package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.favourite.FavouriteStore
import net.matsudamper.kotpub.inbox.EarlyUndoneLikes

internal class RecordedFavourites(
    private val favouriteRepository: FavouriteRepository,
) : FavouriteStore, EarlyUndoneLikes {
    override fun add(favourite: FavouriteStore.ReceivedFavourite): Boolean {
        return favouriteRepository.add(
            notePublicId = PublicNoteId(favourite.notePublicId.value),
            actor = favourite.actor.toRecord(),
            receivedAt = favourite.receivedAt,
        )
    }

    override fun removeByNote(
        notePublicId: KotpubPublicNoteId,
        actorUri: String,
    ): Boolean = favouriteRepository.remove(notePublicId = PublicNoteId(notePublicId.value), actorUri = actorUri)

    override fun removeActor(actorUri: String): Int = favouriteRepository.removeActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = favouriteRepository.findPublicKeyPem(actorUri)

    override fun remember(
        actorUri: String,
        activityUri: String,
        expiresAt: Instant,
    ) {
        favouriteRepository.rememberEarlyUndone(actorUri = actorUri, activityUri = activityUri, expiresAt = expiresAt)
    }

    override fun isRemembered(
        actorUri: String,
        activityUri: String,
        now: Instant,
    ): Boolean = favouriteRepository.isEarlyUndone(actorUri = actorUri, activityUri = activityUri, now = now)
}
