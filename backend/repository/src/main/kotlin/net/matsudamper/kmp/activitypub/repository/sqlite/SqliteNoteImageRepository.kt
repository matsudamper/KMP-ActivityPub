package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.NewNoteImage
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.repository.NoteImageId
import net.matsudamper.kmp.activitypub.repository.NoteImageRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_IMAGES
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.jooq.DSLContext
import org.jooq.Record

internal class SqliteNoteImageRepository(
    private val jooq: SqliteJooq,
) : NoteImageRepository {
    override fun add(image: NewNoteImage): Boolean = jooq.transaction { dsl ->
        val accountExists = dsl.fetchExists(ACCOUNTS, ACCOUNTS.ID.eq(image.accountId.value))
        if (!accountExists) return@transaction false

        dsl.insertInto(NOTE_IMAGES)
            .set(NOTE_IMAGES.ACCOUNT_ID, image.accountId.value)
            .set(NOTE_IMAGES.PUBLIC_ID, image.id.value)
            .set(NOTE_IMAGES.PATH, image.path)
            .set(NOTE_IMAGES.MEDIA_TYPE, image.mediaType)
            .set(NOTE_IMAGES.DESCRIPTION, image.description)
            .set(NOTE_IMAGES.WIDTH, image.width.toLong())
            .set(NOTE_IMAGES.HEIGHT, image.height.toLong())
            .set(NOTE_IMAGES.UPLOADED_AT, StoredInstant.format(image.uploadedAt))
            .execute()
        true
    }

    override fun findUnattached(
        accountId: AccountId,
        ids: List<NoteImageId>,
    ): List<NoteImage> {
        if (ids.isEmpty()) return listOf()

        val found = jooq.withConnection { dsl ->
            dsl.select(NOTE_IMAGES_COLUMNS)
                .from(NOTE_IMAGES)
                .where(NOTE_IMAGES.ACCOUNT_ID.eq(accountId.value))
                .and(NOTE_IMAGES.NOTE_ID.isNull)
                .and(NOTE_IMAGES.PUBLIC_ID.`in`(ids.map { it.value }))
                .fetch { it.toNoteImage() }
                .associateBy { it.id }
        }
        return ids.mapNotNull { found[it] }
    }
}

/**
 * 投稿に添えた画像を、投稿の行 id ごとに並び順で引く
 */
internal fun DSLContext.fetchAttachedNoteImages(noteIds: Collection<Long>): Map<Long, List<NoteImage>> {
    if (noteIds.isEmpty()) return mapOf()

    return select(NOTE_IMAGES.NOTE_ID, *NOTE_IMAGES_COLUMNS.toTypedArray())
        .from(NOTE_IMAGES)
        .where(NOTE_IMAGES.NOTE_ID.`in`(noteIds))
        .orderBy(NOTE_IMAGES.NOTE_ID, NOTE_IMAGES.SORT_ORDER)
        .fetch { checkNotNull(it.get(NOTE_IMAGES.NOTE_ID)) to it.toNoteImage() }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
}

private val NOTE_IMAGES_COLUMNS =
    listOf(
        NOTE_IMAGES.PUBLIC_ID,
        NOTE_IMAGES.PATH,
        NOTE_IMAGES.MEDIA_TYPE,
        NOTE_IMAGES.DESCRIPTION,
        NOTE_IMAGES.WIDTH,
        NOTE_IMAGES.HEIGHT,
    )

private fun Record.toNoteImage(): NoteImage =
    NoteImage(
        id = NoteImageId(checkNotNull(get(NOTE_IMAGES.PUBLIC_ID))),
        path = checkNotNull(get(NOTE_IMAGES.PATH)),
        mediaType = checkNotNull(get(NOTE_IMAGES.MEDIA_TYPE)),
        description = get(NOTE_IMAGES.DESCRIPTION),
        width = checkNotNull(get(NOTE_IMAGES.WIDTH)).toInt(),
        height = checkNotNull(get(NOTE_IMAGES.HEIGHT)).toInt(),
    )
