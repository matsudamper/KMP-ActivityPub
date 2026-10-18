package net.matsudamper.kmp.activitypub.federation

import java.time.Instant
import net.matsudamper.kotpub.entity.PublicNoteId
import net.matsudamper.kotpub.favourite.FavouriteStore
import net.matsudamper.kotpub.inbox.EarlyUndoneLikes
import net.matsudamper.kotpub.inbox.InboxDomainBlocks
import net.matsudamper.kotpub.stamp.StampStore

/**
 * 投稿がまだ無いので、お気に入りとスタンプは受け取っても記録しない
 */
internal object UnsupportedFavouriteStore : FavouriteStore {
    override fun add(favourite: FavouriteStore.ReceivedFavourite): Boolean = false

    override fun removeByNote(
        notePublicId: PublicNoteId,
        actorUri: String,
    ): Boolean = false

    override fun removeActor(actorUri: String): Int = 0

    override fun findPublicKeyPem(actorUri: String): String? = null
}

internal object UnsupportedStampStore : StampStore {
    override fun put(stamp: StampStore.ReceivedStamp): Boolean = false

    override fun remove(
        notePublicId: PublicNoteId,
        actorUri: String,
        emoji: String,
    ): Boolean = false

    override fun removeActor(actorUri: String): Int = 0

    override fun findPublicKeyPem(actorUri: String): String? = null
}

internal object UnsupportedEarlyUndoneLikes : EarlyUndoneLikes {
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

internal object NoInboxDomainBlocks : InboxDomainBlocks {
    override fun blocksInboxFrom(url: String): Boolean = false

    override fun signedRequestReceived(verifiedSignerActorId: String) = Unit
}
