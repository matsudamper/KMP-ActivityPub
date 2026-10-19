package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.Repositories

internal class SqliteRepositories(
    config: DatabaseConfig,
) : Repositories {
    private val connectionManager = SqliteConnectionManager(config)
    private val jooq = SqliteJooq(connectionManager)

    override val accountRepository: AccountRepository = SqliteAccountRepository(jooq)
    override val followerRepository: FollowerRepository = SqliteFollowerRepository(jooq)

    override fun close() {
        connectionManager.close()
    }
}
