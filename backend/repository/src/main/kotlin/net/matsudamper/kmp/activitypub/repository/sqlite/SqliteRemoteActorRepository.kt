package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchCursor
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchRecord
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.REMOTE_ACTORS
import org.jooq.impl.DSL

internal class SqliteRemoteActorRepository(
    private val jooq: SqliteJooq,
) : RemoteActorRepository {
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
            .select(REMOTE_ACTORS.ACTOR_URI, REMOTE_ACTORS.PREFERRED_USERNAME, REMOTE_ACTORS.DISPLAY_NAME)
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
                )
            }
    }
}
