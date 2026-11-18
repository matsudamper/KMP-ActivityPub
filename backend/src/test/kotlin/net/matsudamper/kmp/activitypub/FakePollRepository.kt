package net.matsudamper.kmp.activitypub

import net.matsudamper.kmp.activitypub.repository.PollRecord
import net.matsudamper.kmp.activitypub.repository.PollRepository
import net.matsudamper.kmp.activitypub.repository.PollVoteInput
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

internal class FakePollRepository : PollRepository {
    val votes = mutableListOf<PollVoteInput>()

    override fun localPolls(publicIds: Set<PublicNoteId>): Map<PublicNoteId, PollRecord> = mapOf()

    override fun remotePolls(objectUris: Set<String>): Map<String, PollRecord> = mapOf()

    override fun recordVote(vote: PollVoteInput): Boolean = votes.add(vote)
}
