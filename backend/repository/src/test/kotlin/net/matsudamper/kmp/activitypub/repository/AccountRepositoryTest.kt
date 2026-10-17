package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class AccountRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-account-test")
    private val dbPath: Path = tempDir.resolve("test.db")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `登録したアカウントを id で引ける`() {
        insertAccount()

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val id = checkNotNull(repositories.accountRepository.findCredential("user1")).id
            assertEquals(
                Account(id = id, username = "user1", createdAt = CREATED_AT),
                repositories.accountRepository.findById(id),
            )
        }
    }

    @Test
    fun `登録したアカウントを名前で引ける`() {
        insertAccount()

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val id = checkNotNull(repositories.accountRepository.findCredential("user1")).id
            assertEquals(
                Account(id = id, username = "user1", createdAt = CREATED_AT),
                repositories.accountRepository.findByUsername("USER1"),
            )
        }
    }

    @Test
    fun `いない名前のアカウントは引けない`() {
        TestSchema.applyTo(dbPath)

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertNull(repositories.accountRepository.findByUsername("user1"))
        }
    }

    @Test
    fun `照合に使うハッシュと方式を名前で引ける`() {
        insertAccount()

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val id = checkNotNull(repositories.accountRepository.findCredential("user1")).id
            assertEquals(
                AccountCredential(id = id, username = "user1", passwordHash = PASSWORD_HASH, passwordHashType = PASSWORD_HASH_TYPE),
                repositories.accountRepository.findCredential("user1"),
            )
        }
    }

    @Test
    fun `大文字小文字の違いは同じ名前として扱う`() {
        insertAccount()

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val id = checkNotNull(repositories.accountRepository.findCredential("user1")).id
            assertEquals(id, repositories.accountRepository.findCredential("USER1")?.id)
        }
    }

    @Test
    fun `いない名前は引けない`() {
        TestSchema.applyTo(dbPath)

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertNull(repositories.accountRepository.findCredential("user1"))
        }
    }

    private fun insertAccount() {
        TestSchema.applyTo(dbPath)
        Accounts.insert(
            databasePath = dbPath,
            username = "user1",
            passwordHash = PASSWORD_HASH,
            passwordHashType = PASSWORD_HASH_TYPE,
            createdAt = CREATED_AT.toString(),
        )
    }

    private companion object {
        const val PASSWORD_HASH = "hash"

        const val PASSWORD_HASH_TYPE = "hash-type"

        val CREATED_AT: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
