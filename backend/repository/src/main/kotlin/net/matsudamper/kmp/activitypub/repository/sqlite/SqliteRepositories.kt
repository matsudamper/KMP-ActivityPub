package net.matsudamper.kmp.activitypub.repository.sqlite

import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.FollowingRepository
import net.matsudamper.kmp.activitypub.repository.NoteRepository
import net.matsudamper.kmp.activitypub.repository.PasskeyRepository
import net.matsudamper.kmp.activitypub.repository.RemoteActorRepository
import net.matsudamper.kmp.activitypub.repository.Repositories
import net.matsudamper.kmp.activitypub.repository.TimelineRepository

internal class SqliteRepositories(
    config: DatabaseConfig,
) : Repositories {
    private val connectionManager = SqliteConnectionManager(config)
    private val jooq = SqliteJooq(connectionManager)
    override val followingRepository: FollowingRepository = SqliteFollowingRepository(jooq)
    override val timelineRepository: TimelineRepository = SqliteTimelineRepository(jooq)

    override val accountRepository: AccountRepository = SqliteAccountRepository(jooq)
    override val followerRepository: FollowerRepository = SqliteFollowerRepository(jooq)
    override val deliveryQueueRepository: DeliveryQueueRepository = SqliteDeliveryQueueRepository(jooq)
    override val noteRepository: NoteRepository = SqliteNoteRepository(jooq)
    override val remoteActorRepository: RemoteActorRepository = SqliteRemoteActorRepository(jooq)

    override val passkeyRepository: PasskeyRepository = SqlitePasskeyRepository(jooq)

    override fun close() {
        connectionManager.close()
    }
}
