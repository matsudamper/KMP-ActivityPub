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
                Account(id = id, username = "user1", createdAt = CREATED_AT, displayName = null),
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
                Account(id = id, username = "user1", createdAt = CREATED_AT, displayName = null),
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

    @Test
    fun `登録したアカウントの公開鍵を名前で引ける`() {
        insertAccount()

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals(PUBLIC_KEY_PEM, repositories.accountRepository.findPublicKeyPem("User1"))
        }
    }

    @Test
    fun `名前の一部で大文字小文字を区別せず名前順に探せる`() {
        TestSchema.applyTo(dbPath)
        listOf("bob", "alice", "malice", "carol").forEach { insertAccountRow(it) }

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals(
                listOf("alice", "malice"),
                repositories.accountRepository
                    .searchByUsername(keyword = "ALI", afterUsername = null, limit = 10)
                    .map { it.username },
            )
        }
    }

    @Test
    fun `ASCII 以外の大文字を含む検索語でも探せる`() {
        TestSchema.applyTo(dbPath)
        insertAccountRow("Älice")

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals(
                listOf("älice"),
                repositories.accountRepository
                    .searchByUsername(keyword = "Ä", afterUsername = null, limit = 10)
                    .map { it.username },
            )
        }
    }

    @Test
    fun `指定した名前より後ろを件数まで探せる`() {
        TestSchema.applyTo(dbPath)
        listOf("user1", "user2", "user3", "user4").forEach { insertAccountRow(it) }

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals(
                listOf("user2", "user3"),
                repositories.accountRepository
                    .searchByUsername(keyword = "user", afterUsername = "user1", limit = 2)
                    .map { it.username },
            )
        }
    }

    @Test
    fun `検索語のワイルドカードは文字そのものとして扱う`() {
        TestSchema.applyTo(dbPath)
        listOf("user_1", "userx1").forEach { insertAccountRow(it) }

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            assertEquals(
                listOf("user_1"),
                repositories.accountRepository
                    .searchByUsername(keyword = "_", afterUsername = null, limit = 10)
                    .map { it.username },
            )
            assertEquals(
                listOf(),
                repositories.accountRepository
                    .searchByUsername(keyword = "%", afterUsername = null, limit = 10)
                    .map { it.username },
            )
        }
    }

    private fun insertAccountRow(username: String) {
        Accounts.insert(
            databasePath = dbPath,
            username = username,
            passwordHash = PASSWORD_HASH,
            passwordHashType = PASSWORD_HASH_TYPE,
            publicKeyPem = PUBLIC_KEY_PEM,
            privateKeyPem = "private-key",
            createdAt = CREATED_AT.toString(),
        )
    }

    private fun insertAccount() {
        TestSchema.applyTo(dbPath)
        Accounts.insert(
            databasePath = dbPath,
            username = "user1",
            passwordHash = PASSWORD_HASH,
            passwordHashType = PASSWORD_HASH_TYPE,
            publicKeyPem = PUBLIC_KEY_PEM,
            privateKeyPem = "private-key",
            createdAt = CREATED_AT.toString(),
        )
    }

    private companion object {
        const val PASSWORD_HASH = "hash"

        const val PASSWORD_HASH_TYPE = "hash-type"

        const val PUBLIC_KEY_PEM = "public-key"

        val CREATED_AT: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
