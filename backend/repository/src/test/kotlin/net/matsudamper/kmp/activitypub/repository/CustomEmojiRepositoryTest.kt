package net.matsudamper.kmp.activitypub.repository

import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.shared.AccountId

class CustomEmojiRepositoryTest {
    private val tempDir: Path = createTempDirectory("kmp-activitypub-custom-emoji-test")
    private val dbPath: Path = tempDir.resolve("test.db")

    @BeforeTest
    fun setUp() {
        TestSchema.applyTo(dbPath)
        listOf("alice", "bob").forEach { username ->
            Accounts.insert(
                databasePath = dbPath,
                username = username,
                passwordHash = "hash",
                passwordHashType = "hash-type",
                publicKeyPem = "public-key",
                privateKeyPem = "private-key",
                createdAt = NOW.toString(),
            )
        }
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun sameShortcodeCannotBeAddedTwice() = withRepositories { repositories, alice, bob ->
        val emojis = repositories.customEmojiRepository
        assertNotNull(emojis.add(newEmoji("kotlin", alice)))
        assertNull(emojis.add(newEmoji("kotlin", bob)))

        assertEquals(listOf("kotlin"), emojis.all().map { it.shortcode })
        assertEquals(alice, emojis.findByShortcode("kotlin")?.uploadedBy)
        assertEquals("kotlin", emojis.findByPublicId("public-kotlin")?.shortcode)
    }

    @Test
    fun addedEmojiIsTheSameAsTheStoredRow() = withRepositories { repositories, alice, _ ->
        val emojis = repositories.customEmojiRepository
        val added = assertNotNull(emojis.add(newEmoji("kotlin", alice)))

        assertEquals(added, emojis.findById(added.id))
    }

    @Test
    fun allIsSortedByShortcodeAndSharedByEveryone() = withRepositories { repositories, alice, bob ->
        val emojis = repositories.customEmojiRepository
        emojis.add(newEmoji("zebra", alice))
        emojis.add(newEmoji("apple", bob))

        assertEquals(listOf("apple", "zebra"), emojis.all().map { it.shortcode })
    }

    @Test
    fun onlyTheUploaderCanDelete() = withRepositories { repositories, alice, bob ->
        val emojis = repositories.customEmojiRepository
        val added = assertNotNull(emojis.add(newEmoji("kotlin", alice)))

        assertNull(emojis.delete(added.id, bob))
        assertEquals("kotlin", emojis.delete(added.id, alice)?.shortcode)
        assertEquals(listOf(), emojis.all())
    }

    private fun newEmoji(shortcode: String, uploadedBy: AccountId): NewLocalCustomEmoji = NewLocalCustomEmoji(
        shortcode = shortcode,
        publicId = "public-$shortcode",
        path = "users/${uploadedBy.value}/custom-emojis/$shortcode",
        contentType = "image/png",
        uploadedBy = uploadedBy,
        createdAt = NOW,
    )

    private fun withRepositories(block: (Repositories, AccountId, AccountId) -> Unit) {
        Repositories.create(DatabaseConfig(path = dbPath)).use { repositories ->
            val alice = checkNotNull(repositories.accountRepository.findByUsername("alice")).id
            val bob = checkNotNull(repositories.accountRepository.findByUsername("bob")).id
            block(repositories, alice, bob)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-01T00:00:00Z")
    }
}
