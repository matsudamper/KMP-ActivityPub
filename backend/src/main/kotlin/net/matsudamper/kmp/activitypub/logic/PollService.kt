package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.repository.PollRecord
import net.matsudamper.kmp.activitypub.repository.PollRepository
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef

/**
 * 投稿に付いた投票と票数
 */
class PollService(
    private val polls: PollRepository,
) {
    /**
     * 投票の無い投稿は入らない
     */
    fun pollsOf(refs: Collection<TimelineNoteRef>): Map<TimelineNoteRef, PollRecord> {
        if (refs.isEmpty()) return mapOf()
        val localPolls = polls.localPolls(refs.filterIsInstance<TimelineNoteRef.Local>().map { it.publicId }.toSet())
        val remotePolls = polls.remotePolls(refs.filterIsInstance<TimelineNoteRef.Remote>().map { it.objectUri }.toSet())
        return buildMap {
            refs.forEach { ref ->
                val poll = when (ref) {
                    is TimelineNoteRef.Local -> localPolls[ref.publicId]
                    is TimelineNoteRef.Remote -> remotePolls[ref.objectUri]
                }
                if (poll != null) put(ref, poll)
            }
        }
    }
}
