package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UserSessionRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-user-session-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `期限内のセッションから持ち主を引ける`() {
        withRepositories { repositories ->
            val account = repositories.addAccount()
            repositories.userSessions.create(tokenHash = TOKEN_HASH, accountId = account.id, createdAt = NOW, expiresAt = EXPIRES_AT)

            assertEquals(account.id, repositories.userSessions.findAccountId(tokenHash = TOKEN_HASH, now = NOW))
        }
    }

    @Test
    fun `期限を過ぎたセッションは引けない`() {
        withRepositories { repositories ->
            val account = repositories.addAccount()
            repositories.userSessions.create(tokenHash = TOKEN_HASH, accountId = account.id, createdAt = NOW, expiresAt = EXPIRES_AT)

            assertNull(repositories.userSessions.findAccountId(tokenHash = TOKEN_HASH, now = EXPIRES_AT))
        }
    }

    @Test
    fun `消したセッションは引けない`() {
        withRepositories { repositories ->
            val account = repositories.addAccount()
            repositories.userSessions.create(tokenHash = TOKEN_HASH, accountId = account.id, createdAt = NOW, expiresAt = EXPIRES_AT)

            repositories.userSessions.delete(TOKEN_HASH)

            assertNull(repositories.userSessions.findAccountId(tokenHash = TOKEN_HASH, now = NOW))
        }
    }

    @Test
    fun `アカウントを消すとセッションも引けなくなる`() {
        withRepositories { repositories ->
            val account = repositories.addAccount()
            repositories.userSessions.create(tokenHash = TOKEN_HASH, accountId = account.id, createdAt = NOW, expiresAt = EXPIRES_AT)

            repositories.accounts.markDeleted(
                AccountDeletion(
                    id = account.id,
                    username = account.username,
                    body = """{"type":"Delete"}""",
                    inboxes = listOf(),
                    deletedAt = NOW,
                ),
            )

            assertNull(repositories.userSessions.findAccountId(tokenHash = TOKEN_HASH, now = NOW))
        }
    }

    private fun Repositories.addAccount(): Account =
        assertNotNull(accounts.add(username = "user1", passwordHash = "hash", createdAt = NOW))

    private fun withRepositories(block: (Repositories) -> Unit) {
        val dbPath = tempDir.resolve("test.db")
        TestSchema.applyTo(dbPath)

        createRepositories(DatabaseConfig(path = dbPath)).use(block)
    }

    private companion object {
        const val TOKEN_HASH = "token-hash"

        val NOW: Instant = Instant.parse("2026-09-01T00:00:00Z")

        val EXPIRES_AT: Instant = NOW.plusSeconds(60)
    }
}
