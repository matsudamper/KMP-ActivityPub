package net.matsudamper.kmp.activitypub.repository.sqlite

import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.conf.Settings
import org.jooq.impl.DSL

internal class SqliteJooq(
    private val connectionManager: SqliteConnectionManager,
) {
    fun <T> withConnection(block: (DSLContext) -> T): T = connectionManager.withConnection { connection ->
        block(DSL.using(connection, SQLDialect.SQLITE, SETTINGS))
    }

    fun <T> transaction(block: (DSLContext) -> T): T = connectionManager.transaction { connection ->
        block(DSL.using(connection, SQLDialect.SQLITE, SETTINGS))
    }

    private companion object {
        init {
            // jOOQ のロゴと豆知識を消す。native バイナリでは効かないので :backend のビルド引数でも渡す
            System.setProperty("org.jooq.no-logo", "true")
            System.setProperty("org.jooq.no-tips", "true")
        }

        val SETTINGS: Settings =
            Settings()
                // SQLite にはスキーマもカタログも無い
                .withRenderSchema(false)
                .withRenderCatalog(false)
    }
}
