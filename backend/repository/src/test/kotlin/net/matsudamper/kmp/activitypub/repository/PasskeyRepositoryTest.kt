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
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.shared.PasskeyUserHandle

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
            val first = repositories.passkeyRepository.userHandle(accountId) { PasskeyUserHandle("handle-1") }
            val second = repositories.passkeyRepository.userHandle(accountId) { PasskeyUserHandle("handle-2") }

            assertEquals(PasskeyUserHandle("handle-1"), first)
            assertEquals(PasskeyUserHandle("handle-1"), second)
        }
    }

    @Test
    fun `登録したパスキーを credential ID で引ける`() {
        withRepositories { repositories, accountId ->
            repositories.passkeyRepository.userHandle(accountId) { PasskeyUserHandle("handle-1") }
            repositories.passkeyRepository.add(
                accountId = accountId,
                credentialId = PasskeyCredentialId("credential-1"),
                attestedCredentialData = byteArrayOf(1, 2),
                signCount = 3,
                name = "端末1",
                createdAt = NOW,
            )

            val passkey = checkNotNull(repositories.passkeyRepository.findByCredentialId(PasskeyCredentialId("credential-1")))

            assertEquals(accountId, passkey.accountId)
            assertEquals(PasskeyUserHandle("handle-1"), passkey.userHandle)
            assertContentEquals(byteArrayOf(1, 2), passkey.attestedCredentialData)
            assertEquals(3, passkey.signCount)
            assertEquals(listOf(PasskeyCredentialId("credential-1") to "端末1"), repositories.passkeyRepository.passkeys(accountId).map { it.credentialId to it.name })
        }
    }

    @Test
    fun `同じ credential ID は二度登録できない`() {
        withRepositories { repositories, accountId ->
            assertTrue(
                repositories.passkeyRepository.add(
                    accountId = accountId,
                    credentialId = PasskeyCredentialId("credential-1"),
                    attestedCredentialData = byteArrayOf(1),
                    signCount = 0,
                    name = "端末1",
                    createdAt = NOW,
                ),
            )
            assertFalse(
                repositories.passkeyRepository.add(
                    accountId = accountId,
                    credentialId = PasskeyCredentialId("credential-1"),
                    attestedCredentialData = byteArrayOf(2),
                    signCount = 0,
                    name = "端末1",
                    createdAt = NOW,
                ),
            )
        }
    }

    @Test
    fun `自分のパスキーだけを消せる`() {
        withRepositories { repositories, accountId ->
            repositories.passkeyRepository.add(
                accountId = accountId,
                credentialId = PasskeyCredentialId("credential-1"),
                attestedCredentialData = byteArrayOf(1),
                signCount = 0,
                name = "端末1",
                createdAt = NOW,
            )

            assertFalse(repositories.passkeyRepository.delete(accountId = AccountId(accountId.value + 1), credentialId = PasskeyCredentialId("credential-1")))
            assertTrue(repositories.passkeyRepository.delete(accountId = accountId, credentialId = PasskeyCredentialId("credential-1")))
            assertNull(repositories.passkeyRepository.findByCredentialId(PasskeyCredentialId("credential-1")))
        }
    }

    @Test
    fun `署名回数を更新できる`() {
        withRepositories { repositories, accountId ->
            repositories.passkeyRepository.add(
                accountId = accountId,
                credentialId = PasskeyCredentialId("credential-1"),
                attestedCredentialData = byteArrayOf(1),
                signCount = 0,
                name = "端末1",
                createdAt = NOW,
            )

            repositories.passkeyRepository.updateSignCount(credentialId = PasskeyCredentialId("credential-1"), signCount = 10, usedAt = NOW)

            assertEquals(10, repositories.passkeyRepository.findByCredentialId(PasskeyCredentialId("credential-1"))?.signCount)
        }
    }

    @Test
    fun `署名回数は小さい値では戻らない`() {
        withRepositories { repositories, accountId ->
            repositories.passkeyRepository.add(
                accountId = accountId,
                credentialId = PasskeyCredentialId("credential-1"),
                attestedCredentialData = byteArrayOf(1),
                signCount = 0,
                name = "端末1",
                createdAt = NOW,
            )

            repositories.passkeyRepository.updateSignCount(credentialId = PasskeyCredentialId("credential-1"), signCount = 10, usedAt = NOW)
            repositories.passkeyRepository.updateSignCount(credentialId = PasskeyCredentialId("credential-1"), signCount = 5, usedAt = NOW)

            assertEquals(10, repositories.passkeyRepository.findByCredentialId(PasskeyCredentialId("credential-1"))?.signCount)
        }
    }

    @Test
    fun `無いパスキーは引けない`() {
        withRepositories { repositories, _ ->
            assertNull(repositories.passkeyRepository.findByCredentialId(PasskeyCredentialId("unknown")))
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
            publicKeyPem = "public-key",
            privateKeyPem = "private-key",
            createdAt = NOW.toString(),
        )

        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val accountId = checkNotNull(repositories.accountRepository.findCredential("user1")).id
            block(repositories, accountId)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-09-01T00:00:00Z")
    }
}
