package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.favourite.FavouriteStore
import net.matsudamper.kotpub.inbox.EarlyUndoneLikes
import net.matsudamper.kotpub.stamp.StampStore

/**
 * お気に入りとスタンプはまだ受け付けないので何も残さない
 */
internal object NoReactions : FavouriteStore, StampStore, EarlyUndoneLikes {
    override fun add(favourite: FavouriteStore.ReceivedFavourite): Boolean = false

    override fun removeByNote(
        notePublicId: PublicNoteId,
        actorUri: String,
    ): Boolean = false

    override fun put(stamp: StampStore.ReceivedStamp): Boolean = false

    override fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = false

    override fun removeActor(actorUri: String): Int = 0

    override fun findPublicKeyPem(actorUri: String): String? = null

    override fun remember(
        actorUri: String,
        activityUri: String,
        expiresAt: Instant,
    ) = Unit

    override fun isRemembered(
        actorUri: String,
        activityUri: String,
        now: Instant,
    ): Boolean = false
}
