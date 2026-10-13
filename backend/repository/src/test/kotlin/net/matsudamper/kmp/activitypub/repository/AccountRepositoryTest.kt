package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccountRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-account-test")
    private val dbPath: Path = tempDir.resolve("test.db")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `SQL で入れたアカウントを id で引ける`() {
        TestSchema.applyTo(dbPath)
        val id = TestSchema.insertAccount(dbPath, username = "user1", passwordHash = PASSWORD_HASH, createdAt = CREATED_AT)

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals(
                Account(id = id, username = "user1", createdAt = CREATED_AT),
                repositories.accounts.findById(id),
            )
        }
    }

    @Test
    fun `照合に使うハッシュと方式を名前で引ける`() {
        TestSchema.applyTo(dbPath)
        val id =
            TestSchema.insertAccount(
                dbPath,
                username = "user1",
                passwordHash = PASSWORD_HASH,
                passwordHashType = PASSWORD_HASH_TYPE,
                createdAt = CREATED_AT,
            )

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals(
                AccountCredential(id = id, username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = PASSWORD_HASH_TYPE),
                repositories.accounts.findCredential("user1"),
            )
        }
    }

    @Test
    fun `大文字小文字の違いは同じ名前として扱う`() {
        TestSchema.applyTo(dbPath)
        val id = TestSchema.insertAccount(dbPath, username = "user1", passwordHash = PASSWORD_HASH, createdAt = CREATED_AT)

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals(id, repositories.accounts.findCredential("USER1")?.id)
        }
    }

    @Test
    fun `いない名前は引けない`() {
        TestSchema.applyTo(dbPath)

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertNull(repositories.accounts.findCredential("user1"))
        }
    }

    private companion object {
        const val PASSWORD_HASH = "hash"

        const val PASSWORD_HASH_TYPE = "hash-type"

        val CREATED_AT: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
