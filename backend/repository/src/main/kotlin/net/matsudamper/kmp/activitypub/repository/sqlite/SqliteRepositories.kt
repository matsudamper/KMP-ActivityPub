package net.matsudamper.kmp.activitypub.repository.sqlite

import io.opentelemetry.api.OpenTelemetry
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import net.matsudamper.kmp.activitypub.repository.Repositories

internal class SqliteRepositories(
    config: DatabaseConfig,
    openTelemetry: OpenTelemetry,
) : Repositories {
    private val connectionManager = SqliteConnectionManager(config = config, openTelemetry = openTelemetry)
    private val jooq = SqliteJooq(connectionManager)

    override val accountRepository: AccountRepository = SqliteAccountRepository(jooq)

    override fun close() {
        connectionManager.close()
    }
}
