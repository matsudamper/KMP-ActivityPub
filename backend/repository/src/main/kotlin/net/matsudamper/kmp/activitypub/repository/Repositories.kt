package net.matsudamper.kmp.activitypub.repository

import io.opentelemetry.api.OpenTelemetry
import net.matsudamper.kmp.activitypub.repository.sqlite.SqliteRepositories
import net.matsudamper.kmp.activitypub.session.UserSessionStore

/**
 * JDBC と jOOQ をモジュールの外に出さないための入口
 */
interface Repositories : AutoCloseable {
    val accountRepository: AccountRepository

    val followers: FollowerRepository

    val notes: NoteRepository

    val noteFavourites: NoteFavouriteRepository

    val noteStamps: NoteStampRepository

    val earlyUndoneLikes: EarlyUndoneLikeRepository

    val deliveryQueue: DeliveryQueueRepository

    val domainBlocks: DomainBlockRepository

    val sessionStore: UserSessionStore

    /**
     * 閉じた後は DB のファイルをコピーしたり別のツールで編集したりしてよい
     */
    override fun close()

    companion object {
        fun create(
            config: DatabaseConfig,
            openTelemetry: OpenTelemetry? = null,
        ): Repositories = SqliteRepositories(config, openTelemetry)
    }
}
