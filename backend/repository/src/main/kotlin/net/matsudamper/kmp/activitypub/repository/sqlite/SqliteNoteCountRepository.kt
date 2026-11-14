package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.NoteCountRepository
import net.matsudamper.kmp.activitypub.repository.ReportedNoteCounts
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import org.jooq.impl.DSL

internal class SqliteNoteCountRepository(
    private val jooq: SqliteJooq,
) : NoteCountRepository {
    override fun knownReplyCounts(objectUris: Set<String>): Map<String, Long> {
        if (objectUris.isEmpty()) return mapOf()
        val counted = jooq.withConnection { dsl ->
            val localReplies = dsl.select(NOTES.IN_REPLY_TO, DSL.count())
                .from(NOTES)
                .where(NOTES.IN_REPLY_TO.`in`(objectUris))
                .groupBy(NOTES.IN_REPLY_TO)
            val remoteReplies = dsl.select(REMOTE_NOTES.IN_REPLY_TO, DSL.count())
                .from(REMOTE_NOTES)
                .where(REMOTE_NOTES.IN_REPLY_TO.`in`(objectUris))
                .and(REMOTE_NOTES.DELETED_AT.isNull)
                .and(REMOTE_NOTES.VISIBILITY.`in`(PUBLISHED_VISIBILITIES))
                .groupBy(REMOTE_NOTES.IN_REPLY_TO)
            localReplies.unionAll(remoteReplies)
                .fetch()
                .groupBy(keySelector = { checkNotNull(it.value1()) }, valueTransform = { it.value2().toLong() })
                .mapValues { it.value.sum() }
        }
        return objectUris.associateWith { counted[it] ?: 0L }
    }

    override fun reportedCounts(objectUris: Set<String>): Map<String, ReportedNoteCounts> {
        if (objectUris.isEmpty()) return mapOf()
        return jooq.withConnection { dsl ->
            dsl.select(
                REMOTE_NOTES.OBJECT_URI,
                REMOTE_NOTES.REPORTED_REPLY_COUNT,
                REMOTE_NOTES.REPORTED_FAVOURITE_COUNT,
                REMOTE_NOTES.REPORTED_BOOST_COUNT,
                REMOTE_NOTES.COUNTS_FETCHED_AT,
            )
                .from(REMOTE_NOTES)
                .where(REMOTE_NOTES.OBJECT_URI.`in`(objectUris))
                .and(REMOTE_NOTES.COUNTS_FETCHED_AT.isNotNull)
                .fetch()
                .associate { record ->
                    checkNotNull(record.value1()) to ReportedNoteCounts(
                        replyCount = record.value2(),
                        favouriteCount = record.value3(),
                        boostCount = record.value4(),
                        fetchedAt = StoredInstant.parse(checkNotNull(record.value5())),
                    )
                }
        }
    }

    override fun saveReportedCounts(
        objectUri: String,
        counts: ReportedNoteCounts,
    ): Boolean = jooq.withConnection { dsl ->
        dsl.update(REMOTE_NOTES)
            .set(REMOTE_NOTES.REPORTED_REPLY_COUNT, counts.replyCount)
            .set(REMOTE_NOTES.REPORTED_FAVOURITE_COUNT, counts.favouriteCount)
            .set(REMOTE_NOTES.REPORTED_BOOST_COUNT, counts.boostCount)
            .set(REMOTE_NOTES.COUNTS_FETCHED_AT, StoredInstant.format(counts.fetchedAt))
            .where(REMOTE_NOTES.OBJECT_URI.eq(objectUri))
            .execute() > 0
    }

    private companion object {
        val PUBLISHED_VISIBILITIES = setOf("public", "unlisted")
    }
}
