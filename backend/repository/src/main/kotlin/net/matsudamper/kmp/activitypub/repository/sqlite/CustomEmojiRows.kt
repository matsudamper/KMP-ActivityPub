package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.CustomEmojiRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTOR_EMOJIS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTE_EMOJIS
import org.jooq.DSLContext

/**
 * プロフィールを受け取り直したら、使わなくなった絵文字が残らないよう丸ごと入れ替える
 */
internal fun DSLContext.replaceRemoteActorEmojis(actorUri: String, emojis: List<CustomEmojiRecord>) {
    val actorId = select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).fetchOne(REMOTE_ACTORS.ID) ?: return
    deleteFrom(REMOTE_ACTOR_EMOJIS).where(REMOTE_ACTOR_EMOJIS.REMOTE_ACTOR_ID.eq(actorId)).execute()
    emojis.distinctBy { it.name }.forEach { emoji ->
        insertInto(REMOTE_ACTOR_EMOJIS)
            .set(REMOTE_ACTOR_EMOJIS.REMOTE_ACTOR_ID, actorId)
            .set(REMOTE_ACTOR_EMOJIS.NAME, emoji.name)
            .set(REMOTE_ACTOR_EMOJIS.IMAGE_URL, emoji.imageUrl)
            .execute()
    }
}

internal fun DSLContext.remoteActorEmojisByUri(actorUris: Collection<String>): Map<String, List<CustomEmojiRecord>> {
    if (actorUris.isEmpty()) return mapOf()
    return select(REMOTE_ACTORS.ACTOR_URI, REMOTE_ACTOR_EMOJIS.NAME, REMOTE_ACTOR_EMOJIS.IMAGE_URL)
        .from(REMOTE_ACTOR_EMOJIS)
        .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_ACTOR_EMOJIS.REMOTE_ACTOR_ID))
        .where(REMOTE_ACTORS.ACTOR_URI.`in`(actorUris))
        .fetch()
        .groupBy(
            keySelector = { checkNotNull(it.value1()) },
            valueTransform = { CustomEmojiRecord(name = checkNotNull(it.value2()), imageUrl = checkNotNull(it.value3())) },
        )
}

internal fun DSLContext.insertRemoteNoteEmojis(remoteNoteId: Long, emojis: List<CustomEmojiRecord>) {
    emojis.distinctBy { it.name }.forEach { emoji ->
        insertInto(REMOTE_NOTE_EMOJIS)
            .set(REMOTE_NOTE_EMOJIS.REMOTE_NOTE_ID, remoteNoteId)
            .set(REMOTE_NOTE_EMOJIS.NAME, emoji.name)
            .set(REMOTE_NOTE_EMOJIS.IMAGE_URL, emoji.imageUrl)
            .execute()
    }
}

internal fun DSLContext.remoteNoteEmojisById(remoteNoteIds: Collection<Long>): Map<Long, List<CustomEmojiRecord>> {
    if (remoteNoteIds.isEmpty()) return mapOf()
    return select(REMOTE_NOTE_EMOJIS.REMOTE_NOTE_ID, REMOTE_NOTE_EMOJIS.NAME, REMOTE_NOTE_EMOJIS.IMAGE_URL)
        .from(REMOTE_NOTE_EMOJIS)
        .where(REMOTE_NOTE_EMOJIS.REMOTE_NOTE_ID.`in`(remoteNoteIds))
        .fetch()
        .groupBy(
            keySelector = { checkNotNull(it.value1()) },
            valueTransform = { CustomEmojiRecord(name = checkNotNull(it.value2()), imageUrl = checkNotNull(it.value3())) },
        )
}
