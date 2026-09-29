package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.activitypub.entity.PublicNoteId as MastodonPublicNoteId
import net.matsudamper.activitypub.favourite.FavouriteStore
import net.matsudamper.activitypub.favourite.FavouriteStore.ReceivedFavourite
import net.matsudamper.kmp.activitypub.repository.NoteFavouriteRepository
import net.matsudamper.kmp.activitypub.repository.NoteFavouriteRepository.NewNoteFavourite
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class RepositoryFavouriteStore(
    private val favourites: NoteFavouriteRepository,
) : FavouriteStore {
    override fun add(favourite: ReceivedFavourite): Boolean = favourites.add(
        NewNoteFavourite(
            notePublicId = PublicNoteId(favourite.notePublicId.value),
            actor = StoredRemoteActors.of(favourite.actor),
            receivedAt = favourite.receivedAt,
        ),
    )

    override fun removeByNote(
        notePublicId: MastodonPublicNoteId,
        actorUri: String,
    ): Boolean = favourites.removeByNote(notePublicId = PublicNoteId(notePublicId.value), actorUri = actorUri)

    override fun removeActor(actorUri: String): Int = favourites.removeByActor(actorUri)

    override fun findPublicKeyPem(actorUri: String): String? = favourites.findPublicKeyPem(actorUri)
}
