package net.matsudamper.kmp.activitypub

import net.matsudamper.kmp.activitypub.repository.NoteCountRepository
import net.matsudamper.kmp.activitypub.repository.ReportedNoteCounts

internal class FakeNoteCountRepository : NoteCountRepository {
    /**
     * 返信先の投稿の id ごとの、このサーバーが知っている返信の数
     */
    val knownReplies: MutableMap<String, Long> = mutableMapOf()

    /**
     * 保存している他のサーバーの投稿の id
     */
    val storedRemoteNotes: MutableSet<String> = mutableSetOf()
    private val reported = mutableMapOf<String, ReportedNoteCounts>()

    override fun knownReplyCounts(objectUris: Set<String>): Map<String, Long> = objectUris.associateWith { knownReplies[it] ?: 0L }

    override fun reportedCounts(objectUris: Set<String>): Map<String, ReportedNoteCounts> = reported.filterKeys { it in objectUris }

    override fun saveReportedCounts(objectUri: String, counts: ReportedNoteCounts): Boolean {
        if (objectUri !in storedRemoteNotes) return false
        reported[objectUri] = counts
        return true
    }
}
