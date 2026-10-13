package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import org.sqlite.SQLiteDataSource

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
                statement.executeUpdate(schema)
            }
        }
    }

    fun insertAccount(
        dbPath: Path,
        username: String,
        passwordHash: String,
        passwordHashType: String = "argon2",
        createdAt: Instant,
    ): AccountId {
        val dataSource = SQLiteDataSource().apply { url = "jdbc:sqlite:$dbPath" }
        return dataSource.connection.use { connection ->
            connection
                .prepareStatement("INSERT INTO accounts (username, password_hash, password_hash_type, created_at) VALUES (?, ?, ?, ?) RETURNING id")
                .use { statement ->
                    statement.setString(1, username)
                    statement.setString(2, passwordHash)
                    statement.setString(3, passwordHashType)
                    statement.setString(4, createdAt.toString())
                    statement.executeQuery().use { resultSet ->
                        resultSet.next()
                        AccountId(resultSet.getLong(1))
                    }
                }
        }
    }
}
