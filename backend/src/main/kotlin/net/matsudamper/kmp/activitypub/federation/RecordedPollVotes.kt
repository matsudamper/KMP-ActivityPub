package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import net.matsudamper.kmp.activitypub.repository.PollRepository
import net.matsudamper.kmp.activitypub.repository.PollVoteInput
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kmp.activitypub.timeline.TimelineService
import net.matsudamper.kotpub.note.NoteUrls
import net.matsudamper.kotpub.note.PollVoteStore
import net.matsudamper.kotpub.note.ReceivedPollVote

/**
 * このサーバーの投稿の投票に届いた票を記録し、表示中の画面の票数も更新させる。他のサーバーの投稿を指す票と、投稿者がブロックした相手の票は捨てる
 */
internal class RecordedPollVotes(
    private val domain: String,
    private val polls: PollRepository,
    private val blocks: InboxBlocks,
    private val timeline: TimelineService,
    private val clock: Clock,
) : PollVoteStore {
    override suspend fun vote(vote: ReceivedPollVote) {
        val kotpubPublicId = NoteUrls.publicIdOf(domain = domain, url = vote.questionId) ?: return
        val publicId = PublicNoteId(kotpubPublicId.value)
        if (blocks.blocksOnNote(publicId, vote.voterActorId)) return
        val recorded = polls.recordVote(
            PollVoteInput(
                publicId = publicId,
                actorUri = vote.voterActorId,
                voteUri = vote.id,
                choice = vote.choice,
                votedAt = clock.instant(),
            ),
        )
        if (recorded) timeline.publishLocalNoteChanged(publicId)
    }
}
