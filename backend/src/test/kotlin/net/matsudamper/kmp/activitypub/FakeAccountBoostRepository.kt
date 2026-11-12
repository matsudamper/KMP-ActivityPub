package net.matsudamper.kmp.activitypub

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.AccountBoostPosition
import net.matsudamper.kmp.activitypub.repository.AccountBoostRecord
import net.matsudamper.kmp.activitypub.repository.AccountBoostRepository
import net.matsudamper.kmp.activitypub.repository.BoostTarget
import net.matsudamper.kmp.activitypub.repository.OutgoingBoost
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

/**
 * @param targets member ごとのブーストできる投稿
 */
internal class FakeAccountBoostRepository(
    var targets: Map<String, BoostTarget> = mapOf(),
) : AccountBoostRepository {
    val boosts = mutableListOf<OutgoingBoost>()
    val unboosted = mutableListOf<Pair<AccountId, String>>()

    override fun target(accountId: AccountId, member: String): BoostTarget? = targets[member]

    override fun boostAndEnqueue(boost: OutgoingBoost): TimelinePosition? {
        if (boosts.any { it.accountId == boost.accountId && it.member == boost.member }) return null
        boosts.add(boost)
        return TimelinePosition(boost.boostedAt.toEpochMilli(), "a${boosts.size}")
    }

    override fun unboostAndEnqueueUndo(accountId: AccountId, member: String, now: Instant): Boolean {
        unboosted.add(accountId to member)
        return boosts.removeIf { it.accountId == accountId && it.member == member }
    }

    override fun deleteRemoteNoteAndUndoBoosts(actorUri: String, objectUri: String, now: Instant): Int = 0

    override fun listForOutbox(username: String, after: AccountBoostPosition?, limit: Int): List<AccountBoostRecord> = listOf()

    override fun countForOutbox(username: String): Long = 0

    override fun boostedLocalNotes(accountId: AccountId, publicIds: Set<PublicNoteId>): Set<PublicNoteId> = setOf()
}
