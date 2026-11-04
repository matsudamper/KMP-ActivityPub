package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.FavouriteRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.favourite.FavouriteStore
import net.matsudamper.kotpub.inbox.EarlyUndoneLikes

internal class RecordedFavourites(
    private val favouriteRepository: FavouriteRepository,
) : FavouriteStore, EarlyUndoneLikes {
    override fun add(favourite: FavouriteStore.ReceivedFavourite): Boolean {
        val actor = favourite.actor
        return favouriteRepository.add(
            notePublicId = PublicNoteId(favourite.notePublicId.value),
            actor = RemoteActorRecord(
                actorUri = actor.actorId,
                inbox = actor.inbox,
                sharedInbox = actor.sharedInbox,
                publicKeyPem = actor.publicKeyPem,
                profile = RemoteActorProfileRecord(
                    preferredUsername = actor.profile.preferredUsername,
                    displayName = actor.profile.displayName,
                    profileUrl = actor.profile.profileUrl,
                    iconUrl = actor.profile.iconUrl,
                ),
            ),
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
