package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Duration
import net.matsudamper.kmp.activitypub.repository.NoteCountRepository
import net.matsudamper.kmp.activitypub.repository.ReportedNoteCounts
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kotpub.entity.PublicNoteId as KotpubPublicNoteId
import net.matsudamper.kotpub.note.NoteUrls

/**
 * 投稿のリプライ数と、他のサーバーの投稿のいいね・ブーストの数。
 * 他のサーバーの投稿は、このサーバーが知っている返信しか数えられないので、個別投稿画面を開いたときに相手のサーバーが出している数を取り直す
 */
class PostCountService(
    private val domain: String,
    private val counts: NoteCountRepository,
    private val remoteCounts: RemoteNoteCounts,
    private val clock: Clock,
) {
    fun countsOf(refs: Collection<TimelineNoteRef>): Map<TimelineNoteRef, PostCounts> {
        if (refs.isEmpty()) return mapOf()
        val objectUris = refs.associateWith { objectUriOf(it) }
        val known = counts.knownReplyCounts(objectUris.values.toSet())
        val reported = counts.reportedCounts(refs.filterIsInstance<TimelineNoteRef.Remote>().map { it.objectUri }.toSet())
        return objectUris.mapValues { (_, objectUri) ->
            val reportedCounts = reported[objectUri]
            PostCounts(
                // 相手のサーバーは、ページを途中までしか読まなかったり、このサーバーに届いた返信を数えていなかったりする
                replyCount = maxOf(known[objectUri] ?: 0L, reportedCounts?.replyCount ?: 0L),
                reportedFavouriteCount = reportedCounts?.favouriteCount,
                reportedBoostCount = reportedCounts?.boostCount,
            )
        }
    }

    /**
     * 他のサーバーの投稿で、前に取ってから [REFRESH_INTERVAL] 経っていれば相手のサーバーから取り直す。取れなければ前の数を返す
     */
    suspend fun refreshedCountsOf(ref: TimelineNoteRef): PostCounts {
        if (ref is TimelineNoteRef.Remote) refreshIfStale(ref.objectUri)
        return countsOf(listOf(ref)).getValue(ref)
    }

    private suspend fun refreshIfStale(objectUri: String) {
        val now = clock.instant()
        val fetchedAt = counts.reportedCounts(setOf(objectUri))[objectUri]?.fetchedAt
        if (fetchedAt != null && Duration.between(fetchedAt, now) < REFRESH_INTERVAL) return
        val fetched = remoteCounts.fetch(objectUri) ?: return
        counts.saveReportedCounts(
            objectUri,
            ReportedNoteCounts(
                replyCount = fetched.replyCount,
                favouriteCount = fetched.favouriteCount,
                boostCount = fetched.boostCount,
                fetchedAt = now,
            ),
        )
    }

    private fun objectUriOf(ref: TimelineNoteRef): String = when (ref) {
        is TimelineNoteRef.Local -> NoteUrls(domain, KotpubPublicNoteId(ref.publicId.value)).noteUrl
        is TimelineNoteRef.Remote -> ref.objectUri
    }

    private companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofMinutes(1)
    }
}

/**
 * @param replyCount このサーバーが知っている返信の数と、相手のサーバーが出している数の大きい方
 * @param reportedFavouriteCount 他のサーバーの投稿で、相手のサーバーが出しているいいねの数。出していないか、まだ取っていなければ null
 * @param reportedBoostCount 他のサーバーの投稿で、相手のサーバーが出しているブーストの数。出していないか、まだ取っていなければ null
 */
data class PostCounts(
    val replyCount: Long,
    val reportedFavouriteCount: Long?,
    val reportedBoostCount: Long?,
)

/**
 * 他のサーバーの投稿の数を、相手のサーバーから取る
 */
fun interface RemoteNoteCounts {
    /**
     * @return 取れなければ null
     */
    suspend fun fetch(objectUri: String): FetchedNoteCounts?
}

/**
 * 相手のサーバーが出していない数は null
 */
data class FetchedNoteCounts(
    val replyCount: Long?,
    val favouriteCount: Long?,
    val boostCount: Long?,
)
