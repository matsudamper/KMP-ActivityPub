package net.matsudamper.kmp.activitypub.repository.sqlite

import java.nio.file.Files
import java.sql.Connection
import java.util.concurrent.locks.ReentrantLock
import javax.sql.DataSource
import kotlin.concurrent.withLock
import net.matsudamper.kmp.activitypub.repository.DatabaseConfig
import org.sqlite.SQLiteDataSource

/**
 * SQLite は書き込みが 1 本ずつなので、プールは使わず接続 1 本をロックで直列化する
 */
internal class SqliteConnectionManager(
    config: DatabaseConfig,
) : AutoCloseable {
    private val lock = ReentrantLock()
    private val connection: Connection

    init {
        val path = config.path.toAbsolutePath().normalize()
        // 初回起動時は data/ がまだ無い
        Files.createDirectories(path.parent)

        // DriverManager の ServiceLoader は native-image で追加設定が要る
        val dataSource: DataSource =
            SQLiteDataSource().apply {
                url = "jdbc:sqlite:$path"
            }
        connection = dataSource.connection
        try {
            applyPragmas(connection)
        } catch (e: Throwable) {
            connection.close()
            throw e
        }
    }

    fun <T> withConnection(block: (Connection) -> T): T = lock.withLock { block(connection) }

    fun <T> transaction(block: (Connection) -> T): T =
        lock.withLock {
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                result
            } catch (e: Throwable) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }

    /**
     * -wal を畳んでおかないと、.db だけをコピーしたときに直前の更新が入らない
     */
    override fun close() {
        lock.withLock {
            try {
                connection.createStatement().use { statement ->
                    statement.execute("PRAGMA wal_checkpoint(TRUNCATE)")
                }
            } finally {
                connection.close()
            }
        }
    }

    private companion object {
        fun applyPragmas(connection: Connection) {
            connection.createStatement().use { statement ->
                statement.execute("PRAGMA journal_mode = WAL")
                // 既定で OFF で、接続ごとに要る
                statement.execute("PRAGMA foreign_keys = ON")
                statement.execute("PRAGMA busy_timeout = 5000")
                statement.execute("PRAGMA synchronous = NORMAL")
            }
        }
    }
}
