package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import org.sqlite.SQLiteConfig

internal object SqliteConnections {
    /**
     * SQLite は接続ごとに外部キーが既定で無効になる。
     * 無効のままだと account_passwords の ON DELETE CASCADE が動かない
     */
    fun <T> connect(
        databasePath: Path,
        block: (Connection) -> T,
    ): T {
        val config = SQLiteConfig()
        config.enforceForeignKeys(true)
        return DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}", config.toProperties()).use(block)
    }
}
