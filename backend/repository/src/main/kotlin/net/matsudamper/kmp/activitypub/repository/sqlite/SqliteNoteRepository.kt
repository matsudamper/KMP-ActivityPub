package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.repository.NoteImagePublicId
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.StoredNoteImage
import net.matsudamper.kmp.activitypub.repository.UploadedNoteImage
import net.matsudamper.kmp.activitypub.repository.jooq.tables.records.UploadedNoteImagesRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_IMAGES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.UPLOADED_NOTE_IMAGES
import net.matsudamper.kmp.activitypub.shared.AccountId
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
            .firstOrNull()
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

    override fun findImage(publicId: NoteImagePublicId): StoredNoteImage? = jooq.withConnection { dsl ->
        dsl.select(NOTES.ACCOUNT_ID, NOTE_IMAGES.PUBLIC_ID, NOTE_IMAGES.PATH, NOTE_IMAGES.CONTENT_TYPE, NOTE_IMAGES.DESCRIPTION)
            .from(NOTE_IMAGES)
            .join(NOTES).on(NOTES.ID.eq(NOTE_IMAGES.NOTE_ID))
            .where(NOTE_IMAGES.PUBLIC_ID.eq(publicId.value))
            .fetchOne { record ->
                StoredNoteImage(
                    ownerAccountId = AccountId(checkNotNull(record.get(NOTES.ACCOUNT_ID))),
                    image = record.toNoteImage(),
                )
            }
    }

    override fun addUploadedImage(upload: UploadedNoteImage) {
        jooq.withConnection { dsl ->
            dsl.insertInto(UPLOADED_NOTE_IMAGES)
                .set(UPLOADED_NOTE_IMAGES.PUBLIC_ID, upload.image.publicId.value)
                .set(UPLOADED_NOTE_IMAGES.ACCOUNT_ID, upload.ownerAccountId.value)
                .set(UPLOADED_NOTE_IMAGES.PATH, upload.image.path)
                .set(UPLOADED_NOTE_IMAGES.CONTENT_TYPE, upload.image.contentType)
                .set(UPLOADED_NOTE_IMAGES.DESCRIPTION, upload.image.description)
                .set(UPLOADED_NOTE_IMAGES.EXPIRES_AT, StoredInstant.format(upload.expiresAt))
                .execute()
        }
    }

    override fun findUploadedImages(
        ownerAccountId: AccountId,
        publicIds: List<NoteImagePublicId>,
        now: Instant,
    ): List<NoteImage>? {
        if (publicIds.isEmpty()) return listOf()
        val found =
            jooq.withConnection { dsl ->
                dsl.selectFrom(UPLOADED_NOTE_IMAGES)
                    .where(UPLOADED_NOTE_IMAGES.ACCOUNT_ID.eq(ownerAccountId.value))
                    .and(UPLOADED_NOTE_IMAGES.PUBLIC_ID.`in`(publicIds.map { it.value }))
                    .and(UPLOADED_NOTE_IMAGES.EXPIRES_AT.gt(StoredInstant.format(now)))
                    .fetch { it.toUploadedNoteImage().image }
                    .associateBy { it.publicId }
            }
        return publicIds.map { found[it] ?: return null }
    }

    override fun listExpiredUploadedImages(
        now: Instant,
        limit: Int,
    ): List<UploadedNoteImage> = jooq.withConnection { dsl ->
        dsl.selectFrom(UPLOADED_NOTE_IMAGES)
            .where(UPLOADED_NOTE_IMAGES.EXPIRES_AT.le(StoredInstant.format(now)))
            .orderBy(UPLOADED_NOTE_IMAGES.EXPIRES_AT)
            .limit(limit)
            .fetch { it.toUploadedNoteImage() }
    }

    override fun deleteExpiredUploadedImage(
        publicId: NoteImagePublicId,
        now: Instant,
    ): Boolean = jooq.withConnection { dsl ->
        dsl.deleteFrom(UPLOADED_NOTE_IMAGES)
            .where(UPLOADED_NOTE_IMAGES.PUBLIC_ID.eq(publicId.value))
            .and(UPLOADED_NOTE_IMAGES.EXPIRES_AT.le(StoredInstant.format(now)))
            .execute() > 0
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
            ?.let { PublicNoteId(it) }
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

    /**
     * 画像は投稿ごとに引くと件数分の問い合わせになるので、まとめて引く
     */
    private fun List<Record>.toNotes(dsl: DSLContext): List<Note> {
        val noteIds = map { checkNotNull(it.get(NOTES.ID)) }
        val imagesByNoteId =
            if (noteIds.isEmpty()) {
                mapOf()
            } else {
                dsl.select(NOTE_IMAGES.NOTE_ID, NOTE_IMAGES.PUBLIC_ID, NOTE_IMAGES.PATH, NOTE_IMAGES.CONTENT_TYPE, NOTE_IMAGES.DESCRIPTION)
                    .from(NOTE_IMAGES)
                    .where(NOTE_IMAGES.NOTE_ID.`in`(noteIds))
                    .orderBy(NOTE_IMAGES.NOTE_ID, NOTE_IMAGES.POSITION)
                    .fetch()
                    .groupBy(keySelector = { checkNotNull(it.get(NOTE_IMAGES.NOTE_ID)) }, valueTransform = { it.toNoteImage() })
            }
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

    private fun UploadedNoteImagesRecord.toUploadedNoteImage(): UploadedNoteImage =
        UploadedNoteImage(
            ownerAccountId = AccountId(checkNotNull(accountId)),
            image = NoteImage(
                publicId = NoteImagePublicId(checkNotNull(publicId)),
                path = checkNotNull(path),
                contentType = checkNotNull(contentType),
                description = description,
            ),
            expiresAt = StoredInstant.parse(checkNotNull(expiresAt)),
        )

    private fun Record.toNoteImage(): NoteImage =
        NoteImage(
            publicId = NoteImagePublicId(checkNotNull(get(NOTE_IMAGES.PUBLIC_ID))),
            path = checkNotNull(get(NOTE_IMAGES.PATH)),
            contentType = checkNotNull(get(NOTE_IMAGES.CONTENT_TYPE)),
            description = get(NOTE_IMAGES.DESCRIPTION),
        )
}
