package net.matsudamper.kmp.activitypub.crypto

import java.nio.file.Path
import java.sql.DriverManager
import java.time.Instant
import kotlin.io.path.isRegularFile
import org.sqlite.SQLiteErrorCode
import org.sqlite.SQLiteException

internal object AccountRegistration {
    private val usernamePattern = Regex("^[A-Za-z0-9._-]+$")

    fun register(
        databasePath: Path,
        username: String,
        password: String,
        createdAt: Instant = Instant.now(),
    ) {
        if (!usernamePattern.matches(username)) {
            throw AccountRegistrationException("ユーザー名は英数字と _ . - だけにする: $username")
        }
        if (password.isEmpty()) {
            throw AccountRegistrationException("パスワードを標準入力に 1 行で渡すこと")
        }
        if (!databasePath.isRegularFile()) {
            throw AccountRegistrationException("DB が無い: $databasePath 。sqlite3def でスキーマを適用すること")
        }

        val passwordHash = PasswordHashType.Current.encoder.encode(password)
        val createdAtText = createdAt.toString()

        try {
            DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
                connection.prepareStatement(
                    """
                    INSERT INTO accounts (
                        username,
                        password_hash,
                        password_hash_type,
                        created_at
                    ) VALUES (?, ?, ?, ?)
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, username)
                    statement.setString(2, passwordHash)
                    statement.setString(3, PasswordHashType.Current.dbValue)
                    statement.setString(4, createdAtText)
                    statement.executeUpdate()
                }
            }
        } catch (exception: SQLiteException) {
            if (exception.resultCode == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE) {
                throw AccountRegistrationException("同じユーザー名が既にある: $username")
            }
            if (exception.message?.contains("no such table") == true) {
                throw AccountRegistrationException("accounts が無い。sqlite3def でスキーマを適用すること")
            }
            throw exception
        }
    }
}

internal class AccountRegistrationException(
    message: String,
) : RuntimeException(message)
