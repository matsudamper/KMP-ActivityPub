package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.NotePinRepository
import net.matsudamper.kmp.activitypub.repository.PinNoteResult
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * 投稿の持ち主は確かめず、[pinnedNoteIds] に固定した新しい順で並べる
 */
internal class FakeNotePinRepository : NotePinRepository {
    val pinnedNoteIds = mutableListOf<PublicNoteId>()

    override fun pin(
        accountId: AccountId,
        publicId: PublicNoteId,
        pinnedAt: Instant,
        maxPins: Int,
    ): PinNoteResult {
        if (publicId in pinnedNoteIds) return PinNoteResult.Pinned
        if (pinnedNoteIds.size >= maxPins) return PinNoteResult.TooManyPins
        pinnedNoteIds.add(0, publicId)
        return PinNoteResult.Pinned
    }

    override fun unpin(
        accountId: AccountId,
        publicId: PublicNoteId,
    ): Boolean = pinnedNoteIds.remove(publicId)

    override fun pinnedNotes(username: String): List<PublicNoteId> = pinnedNoteIds.toList()

    override fun pinnedAmong(publicIds: Set<PublicNoteId>): Set<PublicNoteId> = pinnedNoteIds.filter { it in publicIds }.toSet()
}
