package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.NotificationActor
import net.matsudamper.kmp.activitypub.repository.NotificationActorRef
import net.matsudamper.kmp.activitypub.repository.NotificationEntry
import net.matsudamper.kmp.activitypub.repository.NotificationEvent
import net.matsudamper.kmp.activitypub.repository.NotificationGroup
import net.matsudamper.kmp.activitypub.repository.NotificationGroupPosition
import net.matsudamper.kmp.activitypub.repository.NotificationKind
import net.matsudamper.kmp.activitypub.repository.NotificationPostRef
import net.matsudamper.kmp.activitypub.repository.NotificationReaction
import net.matsudamper.kmp.activitypub.repository.NotificationRepository
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.ACCOUNTS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.CUSTOM_EMOJIS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTIFICATION_ENTRIES
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.NOTIFICATION_GROUPS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_NOTES
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.NotificationGroupId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL

internal class SqliteNotificationRepository(
    private val jooq: SqliteJooq,
) : NotificationRepository {
    override fun record(event: NotificationEvent): NotificationGroupId? = jooq.transaction { dsl ->
        val actor = dsl.actorOf(event.actor) ?: return@transaction null
        val target = dsl.targetOf(event) ?: return@transaction null
        if (actor.accountId == target.recipientAccountId) return@transaction null
        val occurredAt = StoredInstant.format(event.occurredAt)

        val existing = dsl.select(NOTIFICATION_ENTRIES.ID, NOTIFICATION_ENTRIES.GROUP_ID)
            .from(NOTIFICATION_ENTRIES)
            .join(NOTIFICATION_GROUPS).on(NOTIFICATION_GROUPS.ID.eq(NOTIFICATION_ENTRIES.GROUP_ID))
            .where(target.groupCondition())
            .and(NOTIFICATION_ENTRIES.ACTOR_ACCOUNT_ID.isNotDistinctFrom(actor.accountId))
            .and(NOTIFICATION_ENTRIES.ACTOR_REMOTE_ACTOR_ID.isNotDistinctFrom(actor.remoteActorId))
            .limit(1)
            .fetchOne()
        if (existing != null) {
            if (event !is NotificationEvent.Favourited) return@transaction null
            dsl.update(NOTIFICATION_ENTRIES)
                .set(NOTIFICATION_ENTRIES.EMOJI, event.reaction?.emoji)
                .set(NOTIFICATION_ENTRIES.EMOJI_IMAGE_URL, event.reaction?.emojiImageUrl)
                .set(NOTIFICATION_ENTRIES.EMOJI_CUSTOM_EMOJI_ID, event.reaction?.customEmojiId?.value)
                .set(NOTIFICATION_ENTRIES.UPDATED_AT, occurredAt)
                .where(NOTIFICATION_ENTRIES.ID.eq(existing.value1()))
                .execute()
            return@transaction NotificationGroupId(checkNotNull(existing.value2()))
        }

        val groupedId = if (target.kind.groupsByNote) {
            dsl.select(NOTIFICATION_GROUPS.ID).from(NOTIFICATION_GROUPS).where(target.groupCondition()).limit(1).fetchOne(NOTIFICATION_GROUPS.ID)
        } else {
            null
        }
        val groupId = groupedId ?: checkNotNull(
            dsl.insertInto(NOTIFICATION_GROUPS)
                .set(NOTIFICATION_GROUPS.ACCOUNT_ID, target.recipientAccountId)
                .set(NOTIFICATION_GROUPS.KIND, target.kind.stored)
                .set(NOTIFICATION_GROUPS.NOTE_ID, target.noteId)
                .set(NOTIFICATION_GROUPS.REMOTE_NOTE_ID, target.remoteNoteId)
                .set(NOTIFICATION_GROUPS.CREATED_AT, occurredAt)
                .set(NOTIFICATION_GROUPS.UPDATED_AT, occurredAt)
                .returningResult(NOTIFICATION_GROUPS.ID)
                .fetchOne(),
        ).value1()
        val reaction = (event as? NotificationEvent.Favourited)?.reaction
        dsl.insertInto(NOTIFICATION_ENTRIES)
            .set(NOTIFICATION_ENTRIES.GROUP_ID, groupId)
            .set(NOTIFICATION_ENTRIES.ACTOR_ACCOUNT_ID, actor.accountId)
            .set(NOTIFICATION_ENTRIES.ACTOR_REMOTE_ACTOR_ID, actor.remoteActorId)
            .set(NOTIFICATION_ENTRIES.EMOJI, reaction?.emoji)
            .set(NOTIFICATION_ENTRIES.EMOJI_IMAGE_URL, reaction?.emojiImageUrl)
            .set(NOTIFICATION_ENTRIES.EMOJI_CUSTOM_EMOJI_ID, reaction?.customEmojiId?.value)
            .set(NOTIFICATION_ENTRIES.CREATED_AT, occurredAt)
            .set(NOTIFICATION_ENTRIES.UPDATED_AT, occurredAt)
            .execute()
        // 届く順と時刻の順が前後しても、並び順を前に戻さない
        dsl.update(NOTIFICATION_GROUPS)
            .set(NOTIFICATION_GROUPS.UPDATED_AT, occurredAt)
            .where(NOTIFICATION_GROUPS.ID.eq(groupId))
            .and(NOTIFICATION_GROUPS.UPDATED_AT.lt(occurredAt))
            .execute()
        NotificationGroupId(checkNotNull(groupId))
    }

    override fun page(accountId: AccountId, before: NotificationGroupPosition?, limit: Int): List<NotificationGroup> = jooq.withConnection { dsl ->
        val older = if (before == null) {
            DSL.noCondition()
        } else {
            DSL.row(NOTIFICATION_GROUPS.UPDATED_AT, NOTIFICATION_GROUPS.ID)
                .lt(DSL.row(DSL.inline(StoredInstant.format(before.updatedAt)), DSL.inline(before.id.value)))
        }
        dsl.groups(NOTIFICATION_GROUPS.ACCOUNT_ID.eq(accountId.value).and(older), limit)
    }

    override fun find(id: NotificationGroupId): NotificationGroup? = jooq.withConnection { dsl ->
        dsl.groups(NOTIFICATION_GROUPS.ID.eq(id.value), 1).firstOrNull()
    }

    private fun DSLContext.groups(condition: Condition, limit: Int): List<NotificationGroup> {
        val rows = select(
            NOTIFICATION_GROUPS.ID,
            NOTIFICATION_GROUPS.ACCOUNT_ID,
            NOTIFICATION_GROUPS.KIND,
            NOTIFICATION_GROUPS.NOTE_ID,
            NOTIFICATION_GROUPS.REMOTE_NOTE_ID,
            NOTIFICATION_GROUPS.CREATED_AT,
            NOTIFICATION_GROUPS.UPDATED_AT,
        ).from(NOTIFICATION_GROUPS)
            .where(condition)
            .orderBy(NOTIFICATION_GROUPS.UPDATED_AT.desc(), NOTIFICATION_GROUPS.ID.desc())
            .limit(limit)
            .fetch()
        val entriesByGroup = entriesOf(rows.map { checkNotNull(it.value1()) })
        return rows.map { row ->
            val id = checkNotNull(row.value1())
            NotificationGroup(
                id = NotificationGroupId(id),
                accountId = AccountId(checkNotNull(row.value2())),
                kind = NotificationKind.entries.first { it.stored == row.value3() },
                noteMember = row.value4()?.let { "l$it" } ?: row.value5()?.let { "r$it" },
                entries = entriesByGroup[id].orEmpty(),
                createdAt = StoredInstant.parse(checkNotNull(row.value6())),
                updatedAt = StoredInstant.parse(checkNotNull(row.value7())),
            )
        }
    }

    private fun DSLContext.entriesOf(groupIds: List<Long>): Map<Long, List<NotificationEntry>> {
        if (groupIds.isEmpty()) return mapOf()
        val rows = select(
            NOTIFICATION_ENTRIES.GROUP_ID, NOTIFICATION_ENTRIES.CREATED_AT, NOTIFICATION_ENTRIES.UPDATED_AT,
            NOTIFICATION_ENTRIES.EMOJI, NOTIFICATION_ENTRIES.EMOJI_IMAGE_URL, CUSTOM_EMOJIS.PUBLIC_ID,
            ACCOUNTS.USERNAME, ACCOUNTS.DISPLAY_NAME,
            REMOTE_ACTORS.ACTOR_URI, REMOTE_ACTORS.PREFERRED_USERNAME, REMOTE_ACTORS.DISPLAY_NAME, REMOTE_ACTORS.ICON_URL, REMOTE_ACTORS.UPDATED_AT,
        ).from(NOTIFICATION_ENTRIES)
            .leftJoin(ACCOUNTS).on(ACCOUNTS.ID.eq(NOTIFICATION_ENTRIES.ACTOR_ACCOUNT_ID))
            .leftJoin(REMOTE_ACTORS).on(REMOTE_ACTORS.ID.eq(NOTIFICATION_ENTRIES.ACTOR_REMOTE_ACTOR_ID))
            .leftJoin(CUSTOM_EMOJIS).on(CUSTOM_EMOJIS.ID.eq(NOTIFICATION_ENTRIES.EMOJI_CUSTOM_EMOJI_ID))
            .where(NOTIFICATION_ENTRIES.GROUP_ID.`in`(groupIds))
            .orderBy(NOTIFICATION_ENTRIES.CREATED_AT.desc(), NOTIFICATION_ENTRIES.ID.desc())
            .fetch()
        val emojisByActor = remoteActorEmojisByUri(rows.mapNotNull { it.value9() }.toSet())
        return rows.groupBy(
            keySelector = { checkNotNull(it.value1()) },
            valueTransform = { row ->
                val remoteActorUri = row.value9()
                NotificationEntry(
                    actor = if (remoteActorUri == null) {
                        NotificationActor(
                            actorUri = null,
                            username = checkNotNull(row.value7()),
                            displayName = row.value8(),
                            iconUrl = null,
                            emojis = listOf(),
                            updatedAt = null,
                        )
                    } else {
                        NotificationActor(
                            actorUri = remoteActorUri,
                            username = row.value10() ?: remoteActorUri,
                            displayName = row.value11(),
                            iconUrl = row.value12(),
                            emojis = emojisByActor[remoteActorUri].orEmpty(),
                            updatedAt = StoredInstant.parse(checkNotNull(row.value13())),
                        )
                    },
                    reaction = row.value4()?.let { emoji ->
                        NotificationReaction(emoji = emoji, emojiImageUrl = row.value5(), localCustomEmojiPublicId = row.value6())
                    },
                    createdAt = StoredInstant.parse(checkNotNull(row.value2())),
                    updatedAt = StoredInstant.parse(checkNotNull(row.value3())),
                )
            },
        )
    }

    private fun DSLContext.actorOf(ref: NotificationActorRef): ActorIds? = when (ref) {
        is NotificationActorRef.Local -> select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.ID.eq(ref.accountId.value)).fetchOne(ACCOUNTS.ID)
            ?.let { ActorIds(accountId = it, remoteActorId = null) }

        is NotificationActorRef.Remote -> select(REMOTE_ACTORS.ID).from(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(ref.actorUri)).fetchOne(REMOTE_ACTORS.ID)
            ?.let { ActorIds(accountId = null, remoteActorId = it) }
    }

    private fun DSLContext.targetOf(event: NotificationEvent): Target? = when (event) {
        is NotificationEvent.Followed -> accountIdOf(event.followeeUsername)
            ?.let { Target(recipientAccountId = it, kind = NotificationKind.FOLLOW, noteId = null, remoteNoteId = null) }

        is NotificationEvent.Mentioned -> mentionTargetOf(event)

        is NotificationEvent.Favourited -> onOwnNote(NotificationKind.FAVOURITE, event.notePublicId)

        is NotificationEvent.Boosted -> onOwnNote(NotificationKind.BOOST, event.notePublicId)
    }

    private fun DSLContext.mentionTargetOf(event: NotificationEvent.Mentioned): Target? {
        val recipientAccountId = accountIdOf(event.mentionedUsername) ?: return null
        return when (val post = event.post) {
            is NotificationPostRef.Local -> findNoteId(post.publicId)
                ?.let { Target(recipientAccountId = recipientAccountId, kind = NotificationKind.MENTION, noteId = it, remoteNoteId = null) }

            is NotificationPostRef.Remote -> select(REMOTE_NOTES.ID).from(REMOTE_NOTES).where(REMOTE_NOTES.OBJECT_URI.eq(post.objectUri)).fetchOne(REMOTE_NOTES.ID)
                ?.let { Target(recipientAccountId = recipientAccountId, kind = NotificationKind.MENTION, noteId = null, remoteNoteId = it) }
        }
    }

    private fun DSLContext.accountIdOf(username: String): Long? =
        select(ACCOUNTS.ID).from(ACCOUNTS).where(ACCOUNTS.USERNAME.eq(username.lowercase())).fetchOne(ACCOUNTS.ID)

    private fun DSLContext.onOwnNote(kind: NotificationKind, publicId: PublicNoteId): Target? =
        select(NOTES.ID, NOTES.ACCOUNT_ID).from(NOTES).where(NOTES.PUBLIC_ID.eq(publicId.value)).fetchOne()
            ?.let { Target(recipientAccountId = checkNotNull(it.value2()), kind = kind, noteId = checkNotNull(it.value1()), remoteNoteId = null) }

    private class ActorIds(val accountId: Long?, val remoteActorId: Long?)

    private class Target(val recipientAccountId: Long, val kind: NotificationKind, val noteId: Long?, val remoteNoteId: Long?) {
        fun groupCondition(): Condition = NOTIFICATION_GROUPS.ACCOUNT_ID.eq(recipientAccountId)
            .and(NOTIFICATION_GROUPS.KIND.eq(kind.stored))
            .and(NOTIFICATION_GROUPS.NOTE_ID.isNotDistinctFrom(noteId))
            .and(NOTIFICATION_GROUPS.REMOTE_NOTE_ID.isNotDistinctFrom(remoteNoteId))
    }
}

internal val NotificationKind.stored: String
    get() = when (this) {
        NotificationKind.FOLLOW -> "follow"
        NotificationKind.MENTION -> "mention"
        NotificationKind.FAVOURITE -> "favourite"
        NotificationKind.BOOST -> "boost"
    }

/**
 * 投稿ごとに 1 項目にまとめる種類。フォローとメンションは 1 件ごとに 1 項目にする
 */
private val NotificationKind.groupsByNote: Boolean
    get() = when (this) {
        NotificationKind.FAVOURITE, NotificationKind.BOOST -> true
        NotificationKind.FOLLOW, NotificationKind.MENTION -> false
    }

/**
 * 他のサーバーのアクターが消えたとき、その相手の記録を消す。誰も残らない項目は読むときに出さない
 */
internal fun DSLContext.deleteNotificationEntriesOf(remoteActorId: Long) {
    deleteFrom(NOTIFICATION_ENTRIES).where(NOTIFICATION_ENTRIES.ACTOR_REMOTE_ACTOR_ID.eq(remoteActorId)).execute()
}
