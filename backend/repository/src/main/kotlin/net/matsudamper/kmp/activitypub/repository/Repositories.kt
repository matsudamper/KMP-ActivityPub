package net.matsudamper.kmp.activitypub.repository

import net.matsudamper.kmp.activitypub.repository.sqlite.SqliteRepositories
import net.matsudamper.kmp.activitypub.session.UserSessionStore

/**
 * JDBC と jOOQ をモジュールの外に出さないための入口
 */
interface Repositories : AutoCloseable {
    val accountRepository: AccountRepository

    val sessionStore: UserSessionStore

    /**
     * 書けなければ例外。native バイナリでは SQLite のネイティブライブラリが壊れていても起動は通るため
     */
    fun verifyWritable()

    /**
     * 閉じた後は DB のファイルをコピーしたり別のツールで編集したりしてよい
     */
    override fun close()

    companion object {
        fun create(config: DatabaseConfig): Repositories = SqliteRepositories(config)
    }
}
