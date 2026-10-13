package net.matsudamper.kmp.activitypub.repository.sqlite

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.repository.jooq.Tables.HEALTH_CHECK
import net.matsudamper.kmp.activitypub.session.UserSessionStore

internal class SqliteRepositories(
    config: DatabaseConfig,
) : Repositories {
    private val connectionManager = SqliteConnectionManager(config)
    private val jooq = SqliteJooq(connectionManager)

    override val accounts: AccountRepository = SqliteAccountRepository(jooq)

    override val userSessions: UserSessionStore = SqliteUserSessionStore(jooq)

    override fun verifyWritable() {
        val writtenAt = Instant.now().toString()

        val readBack =
            jooq.transaction { dsl ->
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

        check(readBack == writtenAt) {
            "DB に書き込んだ値を読み戻せなかった: 書き込み=$writtenAt 読み戻し=$readBack"
        }
    }

    override fun close() {
        connectionManager.close()
    }
}
