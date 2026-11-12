package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.NotificationActor
import net.matsudamper.kmp.activitypub.repository.NotificationKind
import net.matsudamper.kmp.activitypub.repository.NotificationRepository
import net.matsudamper.kmp.activitypub.repository.ReceivedNotification
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTIFICATIONS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.NotificationId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

internal class SqliteNotificationRepository(
    private val jooq: SqliteJooq,
) : NotificationRepository {
    override fun page(accountId: AccountId, after: NotificationId?, limit: Int): List<ReceivedNotification> = jooq.withConnection { dsl ->
        val older = if (after == null) NOTIFICATIONS.ID.isNotNull else NOTIFICATIONS.ID.lt(after.value)
        dsl.notifications(NOTIFICATIONS.ACCOUNT_ID.eq(accountId.value).and(older), limit)
    }

    override fun find(kind: NotificationKind, notePublicId: PublicNoteId, actorUri: String): ReceivedNotification? = jooq.withConnection { dsl ->
        dsl.notifications(
            NOTIFICATIONS.KIND.eq(kind.stored).and(NOTES.PUBLIC_ID.eq(notePublicId.value)).and(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)),
            1,
        ).firstOrNull()
    }

    private fun DSLContext.notifications(condition: Condition, limit: Int): List<ReceivedNotification> {
        val rows = select(
            NOTIFICATIONS.ID, NOTIFICATIONS.ACCOUNT_ID, NOTIFICATIONS.KIND, NOTIFICATIONS.NOTE_ID, NOTIFICATIONS.RECEIVED_AT,
            REMOTE_ACTORS.ACTOR_URI, REMOTE_ACTORS.PREFERRED_USERNAME, REMOTE_ACTORS.DISPLAY_NAME, REMOTE_ACTORS.ICON_URL, REMOTE_ACTORS.UPDATED_AT,
        ).from(NOTIFICATIONS)
            .join(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(NOTIFICATIONS.REMOTE_ACTOR_ID))
            .join(NOTES).on(NOTES.ID.eq(NOTIFICATIONS.NOTE_ID))
            .where(condition)
            .orderBy(NOTIFICATIONS.ID.desc()).limit(limit)
            .fetch()
        val emojisByActor = remoteActorEmojisByUri(rows.map { checkNotNull(it.value6()) }.toSet())
        return rows.map { record ->
            val actorUri = checkNotNull(record.value6())
            ReceivedNotification(
                id = NotificationId(checkNotNull(record.value1())),
                accountId = AccountId(checkNotNull(record.value2())),
                kind = NotificationKind.entries.first { it.stored == record.value3() },
                actor = NotificationActor(
                    actorUri = actorUri,
                    username = record.value7() ?: actorUri,
                    displayName = record.value8(),
                    iconUrl = record.value9(),
                    emojis = emojisByActor[actorUri].orEmpty(),
                    updatedAt = StoredInstant.parse(checkNotNull(record.value10())),
                ),
                noteMember = "l${record.value4()}",
                receivedAt = StoredInstant.parse(checkNotNull(record.value5())),
            )
        }
    }
}

internal val NotificationKind.stored: String
    get() = when (this) {
        NotificationKind.FAVOURITE -> "favourite"
        NotificationKind.BOOST -> "boost"
    }

/**
 * [noteId] の投稿者に通知を残す。同じ相手の同じ種類の通知が既にあれば何もしない
 */
internal fun DSLContext.insertNotification(kind: NotificationKind, noteId: Long, remoteActorId: Long, receivedAt: Instant) {
    insertInto(NOTIFICATIONS, NOTIFICATIONS.ACCOUNT_ID, NOTIFICATIONS.KIND, NOTIFICATIONS.NOTE_ID, NOTIFICATIONS.REMOTE_ACTOR_ID, NOTIFICATIONS.RECEIVED_AT)
        .select(
            select(
                NOTES.ACCOUNT_ID,
                DSL.inline(kind.stored),
                NOTES.ID,
                DSL.inline(remoteActorId),
                DSL.inline(StoredInstant.format(receivedAt)),
            ).from(NOTES).where(NOTES.ID.eq(noteId)),
        )
        .onConflictDoNothing()
        .execute()
}

internal fun DSLContext.deleteNotification(kind: NotificationKind, noteId: Long, remoteActorId: Long) {
    deleteFrom(NOTIFICATIONS)
        .where(NOTIFICATIONS.KIND.eq(kind.stored)).and(NOTIFICATIONS.NOTE_ID.eq(noteId)).and(NOTIFICATIONS.REMOTE_ACTOR_ID.eq(remoteActorId))
        .execute()
}
