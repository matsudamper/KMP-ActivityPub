package net.matsudamper.kmp.activitypub.repository

import java.time.Instant

/**
 * 投稿のリプライ数と、他のサーバーの投稿について相手のサーバーが出している数
 */
interface NoteCountRepository {
    /**
     * このサーバーが知っている返信を数える。他のサーバーの返信は、消されたものと誰でも読めるわけではないものを数えない
     *
     * @param objectUris 返信先の投稿の id
     * @return 返信が無い投稿も 0 で入る
     */
    fun knownReplyCounts(objectUris: Set<String>): Map<String, Long>

    /**
     * @return 保存していて、数を取ったことがある他のサーバーの投稿だけ入る
     */
    fun reportedCounts(objectUris: Set<String>): Map<String, ReportedNoteCounts>

    /**
     * @return 保存していない投稿なら false
     */
    fun saveReportedCounts(
        objectUri: String,
        counts: ReportedNoteCounts,
    ): Boolean
}

/**
 * 他のサーバーの投稿について、相手のサーバーが出している数。出していない数は null
 *
 * @param fetchedAt 相手のサーバーから取った時刻
 */
data class ReportedNoteCounts(
    val replyCount: Long?,
    val favouriteCount: Long?,
    val boostCount: Long?,
    val fetchedAt: Instant,
)
