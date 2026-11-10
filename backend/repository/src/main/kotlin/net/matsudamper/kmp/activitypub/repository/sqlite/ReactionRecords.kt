package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.StampCount
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
 * 他のサーバーから届いたお気に入りと、このサーバーのアカウントのいいねを合わせた数
 */
internal fun DSLContext.favouriteCountOf(noteId: Field<Long?>): Field<Long?> =
    DSL.field(selectCount().from(NOTE_FAVOURITES).where(NOTE_FAVOURITES.NOTE_ID.eq(noteId))).cast(Long::class.java)
        .plus(DSL.field(selectCount().from(NOTE_LIKES).where(NOTE_LIKES.NOTE_ID.eq(noteId))).cast(Long::class.java))

internal fun DSLContext.findNoteId(publicId: PublicNoteId): Long? =
    select(NOTES.ID)
        .from(NOTES)
        .where(NOTES.PUBLIC_ID.eq(publicId.value))
        .fetchOne(NOTES.ID)

/**
 * 投稿ごとに、多く押された絵文字から並べる。数が同じなら先に押された絵文字を前にして、読み込むたびに並びが変わらないようにする
 *
 * @param noteKey 投稿を見分ける NOTES の列
 */
internal fun <K : Any> DSLContext.stampCountsBy(
    noteKey: Field<K?>,
    condition: Condition,
): Map<K, List<StampCount>> {
    val count = DSL.count()
    return select(noteKey, NOTE_STAMPS.EMOJI, NOTE_STAMPS.EMOJI_IMAGE_URL, count)
        .from(NOTE_STAMPS)
        .join(NOTES).on(NOTES.ID.eq(NOTE_STAMPS.NOTE_ID))
        .where(condition)
        .groupBy(noteKey, NOTE_STAMPS.EMOJI, NOTE_STAMPS.EMOJI_IMAGE_URL)
        .orderBy(count.desc(), DSL.min(NOTE_STAMPS.RECEIVED_AT), NOTE_STAMPS.EMOJI, NOTE_STAMPS.EMOJI_IMAGE_URL)
        .fetch()
        .groupBy(
            keySelector = { checkNotNull(it.value1()) },
            valueTransform = {
                StampCount(emoji = checkNotNull(it.value2()), emojiImageUrl = it.value3(), count = it.value4().toLong())
            },
        )
}
