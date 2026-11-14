package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.StampCount
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.CUSTOM_EMOJIS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_FAVOURITES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_LIKES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTE_STAMPS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL

/**
 * お気に入りやスタンプを押した相手を記録する。署名の検証に通った鍵なので、公開鍵も置き換える
 *
 * @return 記録したアクターの id
 */
internal fun DSLContext.upsertRemoteActor(
    actor: RemoteActorRecord,
    updatedAt: Instant,
): Long {
    val storedUpdatedAt = StoredInstant.format(updatedAt)
    val profile = actor.profile
    insertInto(REMOTE_ACTORS)
        .set(REMOTE_ACTORS.ACTOR_URI, actor.actorUri)
        .set(REMOTE_ACTORS.INBOX, actor.inbox)
        .set(REMOTE_ACTORS.SHARED_INBOX, actor.sharedInbox)
        .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, actor.publicKeyPem)
        .set(REMOTE_ACTORS.PREFERRED_USERNAME, profile.preferredUsername)
        .set(REMOTE_ACTORS.DISPLAY_NAME, profile.displayName)
        .set(REMOTE_ACTORS.PROFILE_URL, profile.profileUrl)
        .set(REMOTE_ACTORS.ICON_URL, profile.iconUrl)
        .set(REMOTE_ACTORS.UPDATED_AT, storedUpdatedAt)
        .onConflict(REMOTE_ACTORS.ACTOR_URI)
        .doUpdate()
        .set(REMOTE_ACTORS.INBOX, actor.inbox)
        .set(REMOTE_ACTORS.SHARED_INBOX, actor.sharedInbox)
        .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, actor.publicKeyPem)
        .set(REMOTE_ACTORS.PREFERRED_USERNAME, profile.preferredUsername)
        .set(REMOTE_ACTORS.DISPLAY_NAME, profile.displayName)
        .set(REMOTE_ACTORS.PROFILE_URL, profile.profileUrl)
        .set(REMOTE_ACTORS.ICON_URL, profile.iconUrl)
        .set(REMOTE_ACTORS.UPDATED_AT, storedUpdatedAt)
        .execute()
    replaceRemoteActorEmojis(actor.actorUri, profile.emojis)
    return checkNotNull(
        select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actor.actorUri)).fetchOne(REMOTE_ACTORS.ID),
    ) { "記録したアクターを引けない: ${actor.actorUri}" }
}

/**
 * 他のサーバーから届いたお気に入りと、このサーバーのアカウントのいいねを合わせた数。絵文字を付けたいいねはスタンプに数えるので除く
 */
internal fun DSLContext.favouriteCountOf(noteId: Field<Long?>): Field<Long?> =
    DSL.field(selectCount().from(NOTE_FAVOURITES).where(NOTE_FAVOURITES.NOTE_ID.eq(noteId))).cast(Long::class.java)
        .plus(DSL.field(selectCount().from(NOTE_LIKES).where(NOTE_LIKES.NOTE_ID.eq(noteId)).and(NOTE_LIKES.REACTION_EMOJI.isNull)).cast(Long::class.java))

internal fun DSLContext.findNoteId(publicId: PublicNoteId): Long? =
    select(NOTES.ID)
        .from(NOTES)
        .where(NOTES.PUBLIC_ID.eq(publicId.value))
        .fetchOne(NOTES.ID)

/**
 * 投稿ごとに、他のサーバーから届いたスタンプと、このサーバーのアカウントが絵文字を付けたいいねを合わせて、先に押された絵文字から並べる
 *
 * @param noteKey 投稿を見分ける NOTES の列
 */
internal fun <K : Any> DSLContext.stampCountsBy(
    noteKey: Field<K?>,
    condition: Condition,
): Map<K, List<StampCount>> {
    val noteId = DSL.field(DSL.name("note_id"), Long::class.java)
    val emoji = DSL.field(DSL.name("emoji"), String::class.java)
    val emojiImageUrl = DSL.field(DSL.name("emoji_image_url"), String::class.java)
    val customEmojiId = DSL.field(DSL.name("custom_emoji_id"), Long::class.java)
    val stampedAt = DSL.field(DSL.name("stamped_at"), String::class.java)
    val received = select(
        NOTE_STAMPS.NOTE_ID.`as`(noteId),
        NOTE_STAMPS.EMOJI.`as`(emoji),
        NOTE_STAMPS.EMOJI_IMAGE_URL.`as`(emojiImageUrl),
        DSL.inline(null, Long::class.java).`as`(customEmojiId),
        NOTE_STAMPS.RECEIVED_AT.`as`(stampedAt),
    )
        .from(NOTE_STAMPS)
    // このサーバーのカスタム絵文字の画像は、置いた絵文字の公開 id から URL を作るので、ここでは URL を持たない
    val local = select(
        NOTE_LIKES.NOTE_ID.`as`(noteId),
        NOTE_LIKES.REACTION_EMOJI.`as`(emoji),
        DSL.inline(null, String::class.java).`as`(emojiImageUrl),
        NOTE_LIKES.REACTION_CUSTOM_EMOJI_ID.`as`(customEmojiId),
        NOTE_LIKES.CREATED_AT.`as`(stampedAt),
    )
        .from(NOTE_LIKES)
        .where(NOTE_LIKES.REACTION_EMOJI.isNotNull)
    val stamps = received.unionAll(local).asTable("stamps")
    val stampsNoteId = checkNotNull(stamps.field(noteId))
    val stampsEmoji = checkNotNull(stamps.field(emoji))
    val stampsEmojiImageUrl = checkNotNull(stamps.field(emojiImageUrl))
    val stampsCustomEmojiId = checkNotNull(stamps.field(customEmojiId))
    val firstStampedAt = DSL.min(stamps.field(stampedAt))
    val count = DSL.count()
    return select(noteKey, stampsEmoji, stampsEmojiImageUrl, count, CUSTOM_EMOJIS.PUBLIC_ID)
        .from(stamps)
        .join(NOTES).on(NOTES.ID.eq(stampsNoteId))
        // 消した後に同じ名前で別の画像を置けるので、名前ではなく付けたときの絵文字の id で画像を引く
        .leftJoin(CUSTOM_EMOJIS).on(CUSTOM_EMOJIS.ID.eq(stampsCustomEmojiId))
        .where(condition)
        .groupBy(noteKey, stampsEmoji, stampsEmojiImageUrl, CUSTOM_EMOJIS.PUBLIC_ID)
        .orderBy(firstStampedAt, stampsEmoji, stampsEmojiImageUrl, CUSTOM_EMOJIS.PUBLIC_ID)
        .fetch()
        .groupBy(
            keySelector = { checkNotNull(it.value1()) },
            valueTransform = {
                StampCount(
                    emoji = checkNotNull(it.value2()),
                    emojiImageUrl = it.value3(),
                    count = it.value4().toLong(),
                    localCustomEmojiPublicId = it.value5(),
                )
            },
        )
}
