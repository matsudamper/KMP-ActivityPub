package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileFieldRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTOR_PROFILE_FIELDS
import org.jooq.DSLContext

/**
 * プロフィールを受け取り直したら、消した項目が残らないよう丸ごと入れ替える
 */
internal fun DSLContext.replaceRemoteActorProfileFields(actorUri: String, fields: List<RemoteActorProfileFieldRecord>) {
    val actorId = select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).fetchOne(REMOTE_ACTORS.ID) ?: return
    deleteFrom(REMOTE_ACTOR_PROFILE_FIELDS).where(REMOTE_ACTOR_PROFILE_FIELDS.REMOTE_ACTOR_ID.eq(actorId)).execute()
    fields.forEachIndexed { position, field ->
        insertInto(REMOTE_ACTOR_PROFILE_FIELDS)
            .set(REMOTE_ACTOR_PROFILE_FIELDS.REMOTE_ACTOR_ID, actorId)
            .set(REMOTE_ACTOR_PROFILE_FIELDS.POSITION, position.toLong())
            .set(REMOTE_ACTOR_PROFILE_FIELDS.NAME, field.name)
            .set(REMOTE_ACTOR_PROFILE_FIELDS.HTML_VALUE, field.htmlValue)
            .execute()
    }
}

internal fun DSLContext.remoteActorProfileFields(actorUri: String): List<RemoteActorProfileFieldRecord> =
    select(REMOTE_ACTOR_PROFILE_FIELDS.NAME, REMOTE_ACTOR_PROFILE_FIELDS.HTML_VALUE)
        .from(REMOTE_ACTOR_PROFILE_FIELDS)
        .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(REMOTE_ACTOR_PROFILE_FIELDS.REMOTE_ACTOR_ID))
        .where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri))
        .orderBy(REMOTE_ACTOR_PROFILE_FIELDS.POSITION)
        .fetch { RemoteActorProfileFieldRecord(name = checkNotNull(it.value1()), htmlValue = checkNotNull(it.value2())) }
