package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.shared.AccountId

class PasskeyRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-passkey-test")

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `user handle は一度作ったものを返し続ける`() {
        withRepositories { repositories, accountId ->
            val first = repositories.passkeyRepository.userHandle(accountId) { "handle-1" }
            val second = repositories.passkeyRepository.userHandle(accountId) { "handle-2" }

            assertEquals("handle-1", first)
            assertEquals("handle-1", second)
        }
    }

    @Test
    fun `登録したパスキーを credential ID で引ける`() {
        withRepositories { repositories, accountId ->
            repositories.passkeyRepository.userHandle(accountId) { "handle-1" }
            repositories.passkeyRepository.add(accountId = accountId, credentialId = "credential-1", publicKey = byteArrayOf(1, 2), signCount = 3, createdAt = NOW)

            val passkey = checkNotNull(repositories.passkeyRepository.findByCredentialId("credential-1"))

            assertEquals(accountId, passkey.accountId)
            assertEquals("handle-1", passkey.userHandle)
            assertContentEquals(byteArrayOf(1, 2), passkey.publicKey)
            assertEquals(3, passkey.signCount)
            assertEquals(listOf("credential-1"), repositories.passkeyRepository.credentialIds(accountId))
        }
    }

    @Test
    fun `同じ credential ID は二度登録できない`() {
        withRepositories { repositories, accountId ->
            assertTrue(repositories.passkeyRepository.add(accountId = accountId, credentialId = "credential-1", publicKey = byteArrayOf(1), signCount = 0, createdAt = NOW))
            assertFalse(repositories.passkeyRepository.add(accountId = accountId, credentialId = "credential-1", publicKey = byteArrayOf(2), signCount = 0, createdAt = NOW))
        }
    }

    @Test
    fun `署名回数を更新できる`() {
        withRepositories { repositories, accountId ->
            repositories.passkeyRepository.add(accountId = accountId, credentialId = "credential-1", publicKey = byteArrayOf(1), signCount = 0, createdAt = NOW)

            repositories.passkeyRepository.updateSignCount(credentialId = "credential-1", signCount = 10, usedAt = NOW)

            assertEquals(10, repositories.passkeyRepository.findByCredentialId("credential-1")?.signCount)
        }
    }

    @Test
    fun `challenge は一度だけ使える`() {
        withRepositories { repositories, _ ->
            val store = repositories.passkeyChallengeStore
            store.create(challenge = "c", purpose = PasskeyChallengePurpose.Authentication, accountId = null, createdAt = NOW, expiresAt = EXPIRES_AT)

            assertTrue(store.consume(challenge = "c", purpose = PasskeyChallengePurpose.Authentication, accountId = null, now = NOW))
            assertFalse(store.consume(challenge = "c", purpose = PasskeyChallengePurpose.Authentication, accountId = null, now = NOW))
        }
    }

    @Test
    fun `用途かアカウントが違う challenge は使えず、消えもしない`() {
        withRepositories { repositories, accountId ->
            val store = repositories.passkeyChallengeStore
            store.create(challenge = "c", purpose = PasskeyChallengePurpose.Registration, accountId = accountId, createdAt = NOW, expiresAt = EXPIRES_AT)

            assertFalse(store.consume(challenge = "c", purpose = PasskeyChallengePurpose.Authentication, accountId = accountId, now = NOW))
            assertFalse(store.consume(challenge = "c", purpose = PasskeyChallengePurpose.Registration, accountId = null, now = NOW))
            assertTrue(store.consume(challenge = "c", purpose = PasskeyChallengePurpose.Registration, accountId = accountId, now = NOW))
        }
    }

    @Test
    fun `期限を過ぎた challenge は使えない`() {
        withRepositories { repositories, _ ->
            val store = repositories.passkeyChallengeStore
            store.create(challenge = "c", purpose = PasskeyChallengePurpose.Authentication, accountId = null, createdAt = NOW, expiresAt = EXPIRES_AT)

            assertFalse(store.consume(challenge = "c", purpose = PasskeyChallengePurpose.Authentication, accountId = null, now = EXPIRES_AT))
        }
    }

    @Test
    fun `無いパスキーは引けない`() {
        withRepositories { repositories, _ ->
            assertNull(repositories.passkeyRepository.findByCredentialId("unknown"))
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
        val NOW: Instant = Instant.parse("2026-09-01T00:00:00Z")
        val EXPIRES_AT: Instant = Instant.parse("2026-09-01T00:05:00Z")
    }
}
