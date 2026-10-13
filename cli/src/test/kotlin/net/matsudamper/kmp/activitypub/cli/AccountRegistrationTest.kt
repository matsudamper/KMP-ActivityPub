package net.matsudamper.kmp.activitypub.cli

import java.nio.file.Path
import java.sql.DriverManager
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.crypto.PasswordHashType

class AccountRegistrationTest {
    private val tempDir: Path = createTempDirectory("account-registration")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `パスワードのハッシュを accounts に入れる`() {
        val databasePath = createDatabase()
        val createdAt = Instant.parse("2026-10-13T00:00:00Z")

        AccountRegistration.register(
            databasePath = databasePath,
            username = "user1",
            password = " correct horse ",
            createdAt = createdAt,
        )

        val stored = readAccount(databasePath, "user1")
        assertEquals("user1", stored.username)
        assertEquals(PasswordHashType.Current.dbValue, stored.passwordHashType)
        assertEquals(createdAt.toString(), stored.createdAt)
        assertTrue(PasswordHashType.Current.encoder.matches(" correct horse ", stored.passwordHash))
        assertFalse(PasswordHashType.Current.encoder.matches("correct horse", stored.passwordHash))
    }

    @Test
    fun `大文字小文字だけの別名は登録できない`() {
        val databasePath = createDatabase()
        AccountRegistration.register(
            databasePath = databasePath,
            username = "user1",
            password = "secret",
            createdAt = Instant.parse("2026-10-13T00:00:00Z"),
        )

        val error =
            assertFailsWith<AccountRegistrationException> {
                AccountRegistration.register(
                    databasePath = databasePath,
                    username = "USER1",
                    password = "secret",
                    createdAt = Instant.parse("2026-10-13T00:00:00Z"),
                )
            }
        assertEquals("同じユーザー名が既にある: USER1", error.message)
    }

    @Test
    fun `使えない文字のユーザー名は登録できない`() {
        val databasePath = createDatabase()

        val error =
            assertFailsWith<AccountRegistrationException> {
                AccountRegistration.register(
                    databasePath = databasePath,
                    username = "user 1",
                    password = "secret",
                    createdAt = Instant.parse("2026-10-13T00:00:00Z"),
                )
            }
        assertEquals("ユーザー名は英数字と _ . - だけにする: user 1", error.message)
    }

    @Test
    fun `空のパスワードは登録できない`() {
        val databasePath = createDatabase()

        val error =
            assertFailsWith<AccountRegistrationException> {
                AccountRegistration.register(
                    databasePath = databasePath,
                    username = "user1",
                    password = "",
                    createdAt = Instant.parse("2026-10-13T00:00:00Z"),
                )
            }
        assertEquals("パスワードを標準入力に 1 行で渡すこと", error.message)
    }

    @Test
    fun `DB ファイルが無いときは作らない`() {
        val databasePath = tempDir.resolve("missing.db")

        val error =
            assertFailsWith<AccountRegistrationException> {
                AccountRegistration.register(
                    databasePath = databasePath,
                    username = "user1",
                    password = "secret",
                    createdAt = Instant.parse("2026-10-13T00:00:00Z"),
                )
            }
        assertEquals("DB が無い: $databasePath 。sqlite3def でスキーマを適用すること", error.message)
        assertFalse(databasePath.toFile().exists())
    }

    private fun createDatabase(): Path {
        val databasePath = tempDir.resolve("test.db")
        databasePath.writeText("")
        val schemaPath = Path.of(checkNotNull(System.getProperty("account.schema")))
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(schemaPath.readText())
            }
        }
        return databasePath
    }

    private fun readAccount(
        databasePath: Path,
        username: String,
    ): StoredAccount {
        DriverManager.getConnection("jdbc:sqlite:${databasePath.toAbsolutePath()}").use { connection ->
            connection.prepareStatement(
                """
                SELECT username, password_hash, password_hash_type, created_at
                FROM accounts
                WHERE username = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, username)
                statement.executeQuery().use { result ->
                    check(result.next())
                    return StoredAccount(
                        username = result.getString("username"),
                        passwordHash = result.getString("password_hash"),
                        passwordHashType = result.getString("password_hash_type"),
                        createdAt = result.getString("created_at"),
                    )
                }
            }
        }
    }

    private data class StoredAccount(
        val username: String,
        val passwordHash: String,
        val passwordHashType: String,
        val createdAt: String,
    )
}
