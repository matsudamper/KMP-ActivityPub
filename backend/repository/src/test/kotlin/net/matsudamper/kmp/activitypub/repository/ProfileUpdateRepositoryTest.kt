package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class ProfileUpdateRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-profile-update-test")
    private val dbPath: Path = tempDir.resolve("test.db")

    @BeforeTest
    fun setUp() {
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
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `表示名を書き換えて宛先ごとに Update を積む`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            assertTrue(queue.updateProfile(profileUpdate(displayName = "ユーザー1", body = "update-1")))

            assertEquals("ユーザー1", repositories.accountRepository.findByUsername("user1")?.displayName)
            val claimed = queue.claim(now = NOW, limit = 10)
            assertEquals(INBOXES.toSet(), claimed.map { it.inbox }.toSet())
            assertEquals(listOf("update-1", "update-1"), claimed.map { it.body })
        }
    }

    @Test
    fun `まだ送っていない前の Update は新しい Update で置き換える`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            queue.updateProfile(profileUpdate(displayName = "古い", body = "update-old"))
            queue.updateProfile(profileUpdate(displayName = "新しい", body = "update-new"))

            assertEquals("新しい", repositories.accountRepository.findByUsername("user1")?.displayName)
            assertEquals(listOf("update-new", "update-new"), queue.claim(now = NOW, limit = 10).map { it.body })
        }
    }

    @Test
    fun `表示名を null にすると未設定に戻る`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            queue.updateProfile(profileUpdate(displayName = "ユーザー1", body = "update-1"))
            queue.updateProfile(profileUpdate(displayName = null, body = "update-2"))

            assertNull(repositories.accountRepository.findByUsername("user1")?.displayName)
        }
    }

    @Test
    fun `新しいプロフィール画像の場所を書き、渡さなければ今の場所のまま`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            queue.updateProfile(profileUpdate(displayName = null, body = "update-1").copy(newIconPath = "1/profile-icon/a"))
            assertEquals("1/profile-icon/a", repositories.accountRepository.findByUsername("user1")?.iconPath)

            queue.updateProfile(profileUpdate(displayName = "名前", body = "update-2"))
            assertEquals("1/profile-icon/a", repositories.accountRepository.findByUsername("user1")?.iconPath)
        }
    }

    @Test
    fun `いないアカウントは何も書かない`() {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val queue = repositories.deliveryQueueRepository
            assertFalse(queue.updateProfile(profileUpdate(displayName = "誰か", body = "update").copy(username = "nobody")))

            assertEquals(listOf(), queue.claim(now = NOW, limit = 10))
        }
    }

    private fun profileUpdate(
        displayName: String?,
        body: String,
    ): ProfileUpdate =
        ProfileUpdate(
            username = "user1",
            displayName = displayName,
            newIconPath = null,
            updatedAt = NOW,
            body = body,
            inboxes = INBOXES + INBOXES.first(),
        )

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T00:00:00Z")
        val INBOXES: List<String> = listOf("https://remote1.example/inbox", "https://remote2.example/inbox")
    }
}
