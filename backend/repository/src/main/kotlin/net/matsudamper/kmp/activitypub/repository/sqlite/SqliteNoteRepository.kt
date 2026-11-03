package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL

internal class SqliteNoteRepository(
    private val jooq: SqliteJooq,
) : NoteRepository {
    override fun find(publicId: PublicNoteId): Note? = jooq.withConnection { dsl ->
        dsl.select(NOTES.ID, NOTES.PUBLIC_ID, ACCOUNTS.USERNAME, NOTES.CONTENT_HTML, NOTES.PUBLISHED_AT)
            .from(NOTES)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
            .where(NOTES.PUBLIC_ID.eq(publicId.value))
            .fetch()
            .toNotes(dsl)
            .singleOrNull()
    }

    override fun findByPublicIds(publicIds: Set<PublicNoteId>): Map<PublicNoteId, Note> {
        if (publicIds.isEmpty()) return mapOf()

        return jooq.withConnection { dsl ->
            dsl.select(NOTES.ID, NOTES.PUBLIC_ID, ACCOUNTS.USERNAME, NOTES.CONTENT_HTML, NOTES.PUBLISHED_AT)
                .from(NOTES)
                .join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
                .where(NOTES.PUBLIC_ID.`in`(publicIds.map { it.value }))
                .fetch()
                .toNotes(dsl)
                .associateBy { it.publicId }
        }
    }

    override fun findPublicIdByRequestId(
        username: String,
        requestId: String,
    ): PublicNoteId? = jooq.withConnection { dsl ->
        dsl.select(NOTES.PUBLIC_ID)
            .from(NOTES)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .and(NOTES.REQUEST_ID.eq(requestId))
            .fetchOne(NOTES.PUBLIC_ID)
            ?.let(::PublicNoteId)
    }

    override fun delete(publicId: PublicNoteId) {
        jooq.withConnection { dsl ->
            dsl.deleteFrom(NOTES)
                .where(NOTES.PUBLIC_ID.eq(publicId.value))
                .execute()
        }
    }

    override fun deleteByUsername(username: String): Int = jooq.withConnection { dsl ->
        dsl.deleteFrom(NOTES)
            .where(NOTES.ACCOUNT_ID.`in`(dsl.select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.USERNAME.eq(username.lowercase()))))
            .execute()
    }

    override fun list(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<Note> = jooq.withConnection { dsl ->
        dsl.select(NOTES.ID, NOTES.PUBLIC_ID, ACCOUNTS.USERNAME, NOTES.CONTENT_HTML, NOTES.PUBLISHED_AT)
            .from(NOTES)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .and(olderThan(after))
            .orderBy(NOTES.PUBLISHED_AT.desc(), NOTES.PUBLIC_ID.desc())
            .limit(limit)
            .fetch()
            .toNotes(dsl)
    }

    override fun listPositions(
        username: String,
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = jooq.withConnection { dsl ->
        dsl.select(NOTES.PUBLIC_ID, NOTES.PUBLISHED_AT)
            .from(NOTES)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .and(olderThan(after))
            .orderBy(NOTES.PUBLISHED_AT.desc(), NOTES.PUBLIC_ID.desc())
            .limit(limit)
            .fetch { it.toPosition() }
    }

    override fun listAllPositions(
        after: NotePosition?,
        limit: Int,
    ): List<NotePosition> = jooq.withConnection { dsl ->
        dsl.select(NOTES.PUBLIC_ID, NOTES.PUBLISHED_AT)
            .from(NOTES)
            .where(olderThan(after))
            .orderBy(NOTES.PUBLISHED_AT.desc(), NOTES.PUBLIC_ID.desc())
            .limit(limit)
            .fetch { it.toPosition() }
    }

    override fun count(username: String): Long = jooq.withConnection { dsl ->
        dsl.selectCount()
            .from(NOTES)
            .join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
            .where(ACCOUNTS.USERNAME.eq(username.lowercase()))
            .fetchOne(0, Long::class.java) ?: 0L
    }

    override fun counts(usernames: Set<String>): Map<String, Long> {
        if (usernames.isEmpty()) return mapOf()

        val countedByLowercase = jooq.withConnection { dsl ->
            dsl.select(ACCOUNTS.USERNAME, DSL.count())
                .from(NOTES)
                .join(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTES.ACCOUNT_ID))
                .where(ACCOUNTS.USERNAME.`in`(usernames.map { it.lowercase() }))
                .groupBy(ACCOUNTS.USERNAME)
                .fetch { checkNotNull(it.value1()).lowercase() to it.value2().toLong() }
                .toMap()
        }
        return usernames.associateWith { countedByLowercase[it.lowercase()] ?: 0L }
    }

    private fun olderThan(position: NotePosition?): Condition {
        if (position == null) return DSL.noCondition()
        val publishedAt = StoredInstant.format(position.publishedAt)
        // 時刻だけで比べると、同じ時刻の投稿がページの境目に来たときに落ちるか重なる
        return NOTES.PUBLISHED_AT.lt(publishedAt)
            .or(NOTES.PUBLISHED_AT.eq(publishedAt).and(NOTES.PUBLIC_ID.lt(position.publicId.value)))
    }

    private fun Record.toPosition(): NotePosition =
        NotePosition(
            publishedAt = StoredInstant.parse(checkNotNull(get(NOTES.PUBLISHED_AT))),
            publicId = PublicNoteId(checkNotNull(get(NOTES.PUBLIC_ID))),
        )

    private fun List<Record>.toNotes(dsl: DSLContext): List<Note> {
        val imagesByNoteId = dsl.fetchAttachedNoteImages(map { checkNotNull(it.get(NOTES.ID)) })
        return map { record ->
            Note(
                publicId = PublicNoteId(checkNotNull(record.get(NOTES.PUBLIC_ID))),
                username = checkNotNull(record.get(ACCOUNTS.USERNAME)),
                contentHtml = checkNotNull(record.get(NOTES.CONTENT_HTML)),
                publishedAt = StoredInstant.parse(checkNotNull(record.get(NOTES.PUBLISHED_AT))),
                images = imagesByNoteId[record.get(NOTES.ID)].orEmpty(),
            )
        }
    }
}
