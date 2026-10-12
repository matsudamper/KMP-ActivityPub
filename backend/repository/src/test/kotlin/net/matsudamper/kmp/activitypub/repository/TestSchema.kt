package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.sqlite.SQLiteDataSource

/**
 * 実運用では sqlite3def で手適用する（`db/schema.sql` と同じ場所の README を参照）ので、
 * 起動時の自動適用は無い。テストも同じ前提で、DB を作るときにこれを呼ぶ。
 */
internal object TestSchema {
    fun applyTo(dbPath: Path) {
        val schema =
            checkNotNull(TestSchema::class.java.classLoader.getResourceAsStream("db/schema.sql")) {
                "schema.sql がクラスパスに無い"
            }.use { it.readBytes().toString(Charsets.UTF_8) }

        Files.createDirectories(dbPath.toAbsolutePath().normalize().parent)

        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite:$dbPath" }
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                // sqlite-jdbc は 1 回の executeUpdate で複数の文を実行する
                statement.executeUpdate(schema)
            }
        }
    }

    /**
     * アプリに追加の口は無いので、運用と同じく SQL で直接入れる
     */
    fun insertAccount(
        dbPath: Path,
        username: String,
        passwordHash: String,
        createdAt: Instant,
    ): AccountId {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite:$dbPath" }
        return dataSource.connection.use { connection ->
            connection
                .prepareStatement("INSERT INTO accounts (username, password_hash, created_at) VALUES (?, ?, ?) RETURNING id")
                .use { statement ->
                    statement.setString(1, username)
                    statement.setString(2, passwordHash)
                    statement.setString(3, createdAt.toString())
                    statement.executeQuery().use { resultSet ->
                        resultSet.next()
                        AccountId(resultSet.getLong(1))
                    }
                }
        }
    }
}
