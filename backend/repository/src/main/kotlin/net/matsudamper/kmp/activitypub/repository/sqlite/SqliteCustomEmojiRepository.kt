package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.CustomEmojiRepository
import net.matsudamper.kmp.activitypub.repository.LocalCustomEmoji
import net.matsudamper.kmp.activitypub.repository.NewLocalCustomEmoji
import net.matsudamper.kmp.activitypub.repository.jooq.tables.records.CustomEmojisRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.CUSTOM_EMOJIS
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.CustomEmojiId

internal class SqliteCustomEmojiRepository(
    private val jooq: SqliteJooq,
) : CustomEmojiRepository {
    /**
     * 入れた行の id は INSERT の RETURNING で受け取り、読み直さない。
     * 読み直しを別の文にすると、それが失敗したときに行だけが残り、呼び出し側が消した画像を指す行になる
     */
    override fun add(emoji: NewLocalCustomEmoji): LocalCustomEmoji? = jooq.transaction { dsl ->
        if (dsl.fetchExists(CUSTOM_EMOJIS, CUSTOM_EMOJIS.SHORTCODE.eq(emoji.shortcode))) return@transaction null
        val id = dsl.insertInto(CUSTOM_EMOJIS)
            .set(CUSTOM_EMOJIS.SHORTCODE, emoji.shortcode)
            .set(CUSTOM_EMOJIS.PUBLIC_ID, emoji.publicId)
            .set(CUSTOM_EMOJIS.PATH, emoji.path)
            .set(CUSTOM_EMOJIS.CONTENT_TYPE, emoji.contentType)
            .set(CUSTOM_EMOJIS.UPLOADED_BY_ACCOUNT_ID, emoji.uploadedBy.value)
            .set(CUSTOM_EMOJIS.CREATED_AT, StoredInstant.format(emoji.createdAt))
            .returningResult(CUSTOM_EMOJIS.ID)
            .fetchOne(CUSTOM_EMOJIS.ID)
        LocalCustomEmoji(
            id = CustomEmojiId(checkNotNull(id)),
            shortcode = emoji.shortcode,
            publicId = emoji.publicId,
            path = emoji.path,
            contentType = emoji.contentType,
            uploadedBy = emoji.uploadedBy,
            createdAt = emoji.createdAt,
        )
    }

    override fun all(): List<LocalCustomEmoji> = jooq.withConnection { dsl ->
        dsl.selectFrom(CUSTOM_EMOJIS).orderBy(CUSTOM_EMOJIS.SHORTCODE).fetch { it.toLocalCustomEmoji() }
    }

    override fun findById(id: CustomEmojiId): LocalCustomEmoji? = jooq.withConnection { dsl ->
        dsl.selectFrom(CUSTOM_EMOJIS).where(CUSTOM_EMOJIS.ID.eq(id.value)).fetchOne()?.toLocalCustomEmoji()
    }

    override fun findByShortcode(shortcode: String): LocalCustomEmoji? = jooq.withConnection { dsl ->
        dsl.selectFrom(CUSTOM_EMOJIS).where(CUSTOM_EMOJIS.SHORTCODE.eq(shortcode)).fetchOne()?.toLocalCustomEmoji()
    }

    override fun findByPublicId(publicId: String): LocalCustomEmoji? = jooq.withConnection { dsl ->
        dsl.selectFrom(CUSTOM_EMOJIS).where(CUSTOM_EMOJIS.PUBLIC_ID.eq(publicId)).fetchOne()?.toLocalCustomEmoji()
    }

    override fun delete(id: CustomEmojiId, accountId: AccountId): LocalCustomEmoji? = jooq.withConnection { dsl ->
        dsl.deleteFrom(CUSTOM_EMOJIS)
            .where(CUSTOM_EMOJIS.ID.eq(id.value))
            .and(CUSTOM_EMOJIS.UPLOADED_BY_ACCOUNT_ID.eq(accountId.value))
            .returning()
            .fetchOne()
            ?.toLocalCustomEmoji()
    }

    private fun CustomEmojisRecord.toLocalCustomEmoji(): LocalCustomEmoji = LocalCustomEmoji(
        id = CustomEmojiId(checkNotNull(id)),
        shortcode = checkNotNull(shortcode),
        publicId = checkNotNull(publicId),
        path = checkNotNull(path),
        contentType = checkNotNull(contentType),
        uploadedBy = AccountId(checkNotNull(uploadedByAccountId)),
        createdAt = StoredInstant.parse(checkNotNull(createdAt)),
    )
}
