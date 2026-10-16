package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.jooq.tables.references.HEALTH_CHECK
import org.jooq.SQLDialect
import org.jooq.impl.DSL

object DatabaseWriteCheck {
    /**
     * 書けなければ例外。
     * native バイナリでは SQLite のネイティブライブラリが壊れていても起動は通るため
     */
    fun verify(databasePath: Path) {
        val path = databasePath.toAbsolutePath().normalize()
        path.parent?.let { Files.createDirectories(it) }

        val writtenAt = Instant.now().toString()
        val readBack =
            SqliteConnections.connect(path) { connection ->
                DSL.using(connection, SQLDialect.SQLITE).transactionResult { configuration ->
                    val dsl = configuration.dsl()
                    dsl
                        .insertInto(HEALTH_CHECK)
                        .set(HEALTH_CHECK.ID, 1)
                        .set(HEALTH_CHECK.CHECKED_AT, writtenAt)
                        .onConflict(HEALTH_CHECK.ID)
                        .doUpdate()
                        .set(HEALTH_CHECK.CHECKED_AT, writtenAt)
                        .execute()
                    dsl
                        .select(HEALTH_CHECK.CHECKED_AT)
                        .from(HEALTH_CHECK)
                        .where(HEALTH_CHECK.ID.eq(1))
                        .fetchOne(HEALTH_CHECK.CHECKED_AT)
                }
            }

        check(readBack == writtenAt) {
            "DB に書き込んだ値を読み戻せなかった: 書き込み=$writtenAt 読み戻し=$readBack"
        }
    }
}
