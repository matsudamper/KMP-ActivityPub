package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.shared.AccountId

class UserSessionStoreTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-user-session-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `期限内のセッションから持ち主を引ける`() {
        withRepositories { repositories, accountId ->
            repositories.sessionStore.create(tokenHash = TOKEN_HASH, accountId = accountId, createdAt = NOW, expiresAt = EXPIRES_AT)

            assertEquals(accountId, repositories.sessionStore.findAccountId(tokenHash = TOKEN_HASH, now = NOW))
        }
    }

    @Test
    fun `期限を過ぎたセッションは引けない`() {
        withRepositories { repositories, accountId ->
            repositories.sessionStore.create(tokenHash = TOKEN_HASH, accountId = accountId, createdAt = NOW, expiresAt = EXPIRES_AT)

            assertNull(repositories.sessionStore.findAccountId(tokenHash = TOKEN_HASH, now = EXPIRES_AT))
        }
    }

    private fun withRepositories(block: (Repositories, AccountId) -> Unit) {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)
        Accounts.insert(
            databasePath = dbPath,
            username = "user1",
            passwordHash = "hash",
            passwordHashType = "hash-type",
            createdAt = NOW.toString(),
        )

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val accountId = checkNotNull(repositories.accountRepository.findCredential("user1")).id
            block(repositories, accountId)
        }
    }

    private companion object {
        const val TOKEN_HASH = "token-hash"

        val NOW: Instant = Instant.parse("2026-09-01T00:00:00Z")

        val EXPIRES_AT: Instant = NOW.plusSeconds(60)
    }
}
