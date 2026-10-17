package net.matsudamper.kmp.activitypub.repository

import net.matsudamper.kmp.activitypub.repository.sqlite.SqliteRepositories

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

    /**
     * 閉じた後は DB のファイルをコピーしたり別のツールで編集したりしてよい
     */
    override fun close()

    companion object {
        fun create(config: DatabaseConfig): Repositories = SqliteRepositories(config)
    }
}
