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
                RemoteActorProfileRecord(it.preferredUsername, it.displayName, it.profileUrl, it.iconUrl),
            )
        }
    }

    override fun save(actor: RemoteActorRecord, updatedAt: Instant) {
        jooq.withConnection { dsl ->
            dsl.insertInto(REMOTE_ACTORS)
                .set(REMOTE_ACTORS.ACTOR_URI, actor.actorUri)
                .set(REMOTE_ACTORS.INBOX, actor.inbox)
                .set(REMOTE_ACTORS.SHARED_INBOX, actor.sharedInbox)
                .set(REMOTE_ACTORS.PUBLIC_KEY_PEM, actor.publicKeyPem)
                .set(REMOTE_ACTORS.PREFERRED_USERNAME, actor.profile.preferredUsername)
                .set(REMOTE_ACTORS.DISPLAY_NAME, actor.profile.displayName)
                .set(REMOTE_ACTORS.PROFILE_URL, actor.profile.profileUrl)
                .set(REMOTE_ACTORS.ICON_URL, actor.profile.iconUrl)
                .set(REMOTE_ACTORS.UPDATED_AT, StoredInstant.format(updatedAt))
                .onConflict(REMOTE_ACTORS.ACTOR_URI)
                .doUpdate()
                .set(REMOTE_ACTORS.PREFERRED_USERNAME, actor.profile.preferredUsername)
                .set(REMOTE_ACTORS.DISPLAY_NAME, actor.profile.displayName)
                .set(REMOTE_ACTORS.PROFILE_URL, actor.profile.profileUrl)
                .set(REMOTE_ACTORS.ICON_URL, actor.profile.iconUrl)
                .set(REMOTE_ACTORS.UPDATED_AT, StoredInstant.format(updatedAt))
                .execute()
        }
    }

    override fun findByAcct(username: String, host: String): RemoteActorSearchRecord? = jooq.withConnection { dsl ->
        dsl.selectFrom(REMOTE_ACTORS)
            .where(REMOTE_ACTORS.PREFERRED_USERNAME.eq(username))
            .fetch()
            .firstOrNull { runCatching { URI(it.actorUri).host }.getOrNull().equals(host, ignoreCase = true) }
            ?.let { record ->
                RemoteActorSearchRecord(
                    actorUri = checkNotNull(record.actorUri),
                    preferredUsername = checkNotNull(record.preferredUsername),
                    displayName = record.displayName,
                    iconUrl = record.iconUrl,
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
        dsl
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
            .map { record ->
                RemoteActorSearchRecord(
                    actorUri = checkNotNull(record.get(REMOTE_ACTORS.ACTOR_URI)),
                    preferredUsername = checkNotNull(record.get(REMOTE_ACTORS.PREFERRED_USERNAME)),
                    displayName = record.get(REMOTE_ACTORS.DISPLAY_NAME),
                    iconUrl = record.get(REMOTE_ACTORS.ICON_URL),
                    updatedAt = StoredInstant.parse(checkNotNull(record.get(REMOTE_ACTORS.UPDATED_AT))),
                )
            }
    }
}
