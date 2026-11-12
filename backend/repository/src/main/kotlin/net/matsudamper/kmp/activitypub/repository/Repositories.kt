package net.matsudamper.kmp.activitypub.repository

import net.matsudamper.kmp.activitypub.repository.sqlite.SqliteRepositories

/**
 * JDBC と jOOQ をモジュールの外に出さないための入口
 */
interface Repositories : AutoCloseable {
    val followingRepository: FollowingRepository
    val followingGroupRepository: FollowingGroupRepository
    val timelineRepository: TimelineRepository
    val accountRepository: AccountRepository
    val followerRepository: FollowerRepository
    val deliveryQueueRepository: DeliveryQueueRepository
    val noteRepository: NoteRepository
    val notePinRepository: NotePinRepository
    val remoteActorRepository: RemoteActorRepository
    val favouriteRepository: FavouriteRepository
    val stampRepository: StampRepository
    val boostRepository: BoostRepository
    val likeRepository: LikeRepository
    val accountBoostRepository: AccountBoostRepository

    val passkeyRepository: PasskeyRepository

    /**
     * 閉じた後は DB のファイルをコピーしたり別のツールで編集したりしてよい
     */
    override fun close()

    companion object {
        fun create(config: DatabaseConfig): Repositories = SqliteRepositories(config)
    }
}
