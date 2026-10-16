package net.matsudamper.kmp.activitypub.repository.sqlite

import io.opentelemetry.api.OpenTelemetry
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.DomainBlockRepository
import net.matsudamper.kmp.activitypub.repository.EarlyUndoneLikeRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.NoteFavouriteRepository
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.NoteStampRepository
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.session.UserSessionStore

internal class SqliteRepositories(
    config: DatabaseConfig,
    openTelemetry: OpenTelemetry?,
) : Repositories {
    private val connectionManager = SqliteConnectionManager(config, openTelemetry)
    private val jooq = SqliteJooq(connectionManager)

    override val accountRepository: AccountRepository = SqliteAccountRepository(jooq)

    override val followers: FollowerRepository = SqliteFollowerRepository(jooq)

    override val notes: NoteRepository = SqliteNoteRepository(jooq)

    override val noteFavourites: NoteFavouriteRepository = SqliteNoteFavouriteRepository(jooq)

    override val noteStamps: NoteStampRepository = SqliteNoteStampRepository(jooq)

    override val earlyUndoneLikes: EarlyUndoneLikeRepository = SqliteEarlyUndoneLikeRepository(jooq)

    override val deliveryQueue: DeliveryQueueRepository = SqliteDeliveryQueueRepository(jooq)

    override val domainBlocks: DomainBlockRepository = SqliteDomainBlockRepository(jooq)

    override val sessionStore: UserSessionStore = SqliteUserSessionStore(jooq)

    override fun close() {
        connectionManager.close()
    }
}
