package net.matsudamper.kmp.activitypub.repository.sqlite

import java.net.URI
import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchCursor
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import org.jooq.impl.DSL

internal class SqliteRemoteActorRepository(
    private val jooq: SqliteJooq,
) : RemoteActorRepository {
    override fun findByUri(actorUri: String): RemoteActorRecord? = jooq.withConnection { dsl ->
        dsl.selectFrom(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).fetchOne()?.let {
            RemoteActorRecord(
                checkNotNull(it.actorUri),
                checkNotNull(it.inbox),
                it.sharedInbox,
                checkNotNull(it.publicKeyPem),
                RemoteActorProfileRecord(
                    it.preferredUsername,
                    it.displayName,
                    it.profileUrl,
                    it.iconUrl,
                    it.headerUrl,
                    dsl.remoteActorEmojisByUri(listOf(actorUri))[actorUri].orEmpty(),
                ),
            )
        }
    }

    override fun save(actor: RemoteActorRecord, updatedAt: Instant) {
        jooq.transaction { dsl ->
            dsl.insertInto(REMOTE_ACTORS)
                .set(REMOTE_ACTORS.ACTOR_URI, actor.actorUri)
                .set(REMOTE_ACTORS.INBOX, actor.inbox)
                .set(REMOTE_ACTORS.SHARED_INBOX, actor.sharedInbox)
                .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, actor.publicKeyPem)
                .set(REMOTE_ACTORS.PREFERRED_USERNAME, actor.profile.preferredUsername)
                .set(REMOTE_ACTORS.DISPLAY_NAME, actor.profile.displayName)
                .set(REMOTE_ACTORS.PROFILE_URL, actor.profile.profileUrl)
                .set(REMOTE_ACTORS.ICON_URL, actor.profile.iconUrl)
                .set(REMOTE_ACTORS.HEADER_URL, actor.profile.headerUrl)
                .set(REMOTE_ACTORS.UPDATED_AT, StoredInstant.format(updatedAt))
                .onConflict(REMOTE_ACTORS.ACTOR_URI)
                .doUpdate()
                .set(REMOTE_ACTORS.PREFERRED_USERNAME, actor.profile.preferredUsername)
                .set(REMOTE_ACTORS.DISPLAY_NAME, actor.profile.displayName)
                .set(REMOTE_ACTORS.PROFILE_URL, actor.profile.profileUrl)
                .set(REMOTE_ACTORS.ICON_URL, actor.profile.iconUrl)
                .set(REMOTE_ACTORS.HEADER_URL, actor.profile.headerUrl)
                .set(REMOTE_ACTORS.UPDATED_AT, StoredInstant.format(updatedAt))
                .execute()
            dsl.replaceRemoteActorEmojis(actor.actorUri, actor.profile.emojis)
        }
    }

    override fun remove(actorUri: String) {
        jooq.withConnection { dsl ->
            dsl.deleteFrom(REMOTE_ACTORS).where(REMOTE_ACTORS.ACTOR_URI.eq(actorUri)).execute()
        }
    }

    override fun findByAcct(username: String, host: String): RemoteActorSearchRecord? = jooq.withConnection { dsl ->
        dsl.selectFrom(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.PREFERRED_USERNAME.eq(username))
            .fetch()
            .firstOrNull { runCatching { URI(it.actorUri).host }.getOrNull().equals(host, ignoreCase = true) }
            ?.let { record ->
                val actorUri = checkNotNull(record.actorUri)
                RemoteActorSearchRecord(
                    actorUri = actorUri,
                    preferredUsername = checkNotNull(record.preferredUsername),
                    displayName = record.displayName,
                    iconUrl = record.iconUrl,
                    emojis = dsl.remoteActorEmojisByUri(listOf(actorUri))[actorUri].orEmpty(),
                    updatedAt = StoredInstant.parse(checkNotNull(record.updatedAt)),
                )
            }
    }

    override fun searchByName(
        keyword: String,
        after: RemoteActorSearchCursor?,
        limit: Int,
    ): List<RemoteActorSearchRecord> = jooq.withConnection { dsl ->
        // SQLite の lower() は ASCII しか変換せず、他のサーバーの名前は受け取ったまま保存している。
        // 入力したままの検索語でも Kotlin で小文字にした検索語でも探し、どちらかの書き方で入れれば見つかるようにする
        val keywords = setOf(keyword, keyword.lowercase())
        val sortKey = DSL.lower(REMOTE_ACTORS.PREFERRED_USERNAME)
        val rows = dsl
            .select(REMOTE_ACTORS.ACTOR_URI, REMOTE_ACTORS.PREFERRED_USERNAME, REMOTE_ACTORS.DISPLAY_NAME, REMOTE_ACTORS.ICON_URL, REMOTE_ACTORS.UPDATED_AT)
            .from(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.PREFERRED_USERNAME.isNotNull)
            .and(
                DSL.or(
                    keywords.flatMap { candidate ->
                        listOf(
                            REMOTE_ACTORS.PREFERRED_USERNAME.containsIgnoreCase(candidate),
                            REMOTE_ACTORS.DISPLAY_NAME.containsIgnoreCase(candidate),
                        )
                    },
                ),
            )
            .and(
                if (after == null) {
                    DSL.noCondition()
                } else {
                    DSL.row(sortKey, REMOTE_ACTORS.ACTOR_URI)
                        .gt(DSL.row(DSL.lower(DSL.`val`(after.preferredUsername)), DSL.`val`(after.actorUri)))
                },
            )
            .orderBy(sortKey, REMOTE_ACTORS.ACTOR_URI)
            .limit(limit)
            .fetch()
        val emojisByActor = dsl.remoteActorEmojisByUri(rows.map { checkNotNull(it.get(REMOTE_ACTORS.ACTOR_URI)) })
        rows.map { record ->
            val actorUri = checkNotNull(record.get(REMOTE_ACTORS.ACTOR_URI))
            RemoteActorSearchRecord(
                actorUri = actorUri,
                preferredUsername = checkNotNull(record.get(REMOTE_ACTORS.PREFERRED_USERNAME)),
                displayName = record.get(REMOTE_ACTORS.DISPLAY_NAME),
                iconUrl = record.get(REMOTE_ACTORS.ICON_URL),
                emojis = emojisByActor[actorUri].orEmpty(),
                updatedAt = StoredInstant.parse(checkNotNull(record.get(REMOTE_ACTORS.UPDATED_AT))),
            )
        }
    }

    override fun listActorUris(after: String?, limit: Int): List<String> = jooq.withConnection { dsl ->
        dsl.select(REMOTE_ACTORS.ACTOR_URI)
            .from(REMOTE_ACTORS)
            .where(if (after == null) DSL.noCondition() else REMOTE_ACTORS.ACTOR_URI.gt(after))
            .orderBy(REMOTE_ACTORS.ACTOR_URI)
            .limit(limit)
            .fetch { checkNotNull(it.value1()) }
    }

    override fun count(): Long = jooq.withConnection { dsl ->
        dsl.fetchCount(REMOTE_ACTORS).toLong()
    }
}
