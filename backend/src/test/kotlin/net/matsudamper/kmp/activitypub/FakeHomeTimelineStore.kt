package net.matsudamper.kmp.activitypub

import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.HomeTimelineStore
import net.matsudamper.kmp.activitypub.timeline.TimelineCursor

internal class FakeHomeTimelineStore : HomeTimelineStore {
    val timelines = mutableMapOf<AccountId, List<NotePosition>>()

    override fun add(
        accountId: AccountId,
        position: NotePosition,
        maxSize: Int,
    ) {
        addAll(accountId = accountId, positions = listOf(position), maxSize = maxSize)
    }

    override fun addAll(
        accountId: AccountId,
        positions: List<NotePosition>,
        maxSize: Int,
    ) {
        if (positions.isEmpty()) return
        timelines[accountId] =
            (timelines[accountId].orEmpty() + positions)
                .distinct()
                .sortedByDescending { TimelineCursor.encode(it) }
                .take(maxSize)
    }

    override fun list(
        accountId: AccountId,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition>? {
        val positions = timelines[accountId] ?: return null
        return positions
            .filter { after == null || TimelineCursor.encode(it) < TimelineCursor.encode(after) }
            .take(limit)
    }

    override fun close() = Unit
}
