package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import net.matsudamper.kmp.activitypub.repository.CustomEmojiRepository
import net.matsudamper.kmp.activitypub.repository.LocalCustomEmoji
import net.matsudamper.kmp.activitypub.repository.NewLocalCustomEmoji
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.CustomEmojiId

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class CustomEmojiServiceTest {
    private val repository = InMemoryCustomEmojiRepository()
    private val service = CustomEmojiService(customEmojis = repository, imageStorage = null, clock = Clock.systemUTC())

    @Test
    fun `本文に入っている、置いてある絵文字だけを書かれた順に 1 つずつ返す`() {
        repository.add(newEmoji("blobcat"))
        repository.add(newEmoji("party_2"))

        val found = service.findInNoteContent("<p>:party_2: hello :blobcat::unknown: :party_2: 12:30:45 blobcat</p>")

        assertEquals(listOf("party_2", "blobcat"), found.map { it.shortcode })
    }

    @Test
    fun `消した絵文字は、同じ本文でも返さなくなる`() {
        val blobcat = checkNotNull(repository.add(newEmoji("blobcat")))
        val content = "<p>:blobcat:</p>"
        assertEquals(listOf("blobcat"), service.findInNoteContent(content).map { it.shortcode })

        repository.delete(id = blobcat.id, accountId = UPLOADER)

        assertEquals(listOf(), service.findInNoteContent(content))
    }

    private fun newEmoji(shortcode: String): NewLocalCustomEmoji = NewLocalCustomEmoji(
        shortcode = shortcode,
        publicId = "public-$shortcode",
        path = "users/1/custom-emojis/$shortcode",
        contentType = "image/png",
        uploadedBy = UPLOADER,
        createdAt = Instant.parse("2026-11-01T00:00:00Z"),
    )

    private class InMemoryCustomEmojiRepository : CustomEmojiRepository {
        private val emojis = mutableListOf<LocalCustomEmoji>()

        override fun add(emoji: NewLocalCustomEmoji): LocalCustomEmoji? {
            if (findByShortcode(emoji.shortcode) != null) return null
            val added = LocalCustomEmoji(
                id = CustomEmojiId(emojis.size + 1L),
                shortcode = emoji.shortcode,
                publicId = emoji.publicId,
                path = emoji.path,
                contentType = emoji.contentType,
                uploadedBy = emoji.uploadedBy,
                createdAt = emoji.createdAt,
            )
            emojis += added
            return added
        }

        override fun all(): List<LocalCustomEmoji> = emojis.sortedBy { it.shortcode }

        override fun findById(id: CustomEmojiId): LocalCustomEmoji? = emojis.firstOrNull { it.id == id }

        override fun findByShortcode(shortcode: String): LocalCustomEmoji? = emojis.firstOrNull { it.shortcode == shortcode }

        override fun findByPublicId(publicId: String): LocalCustomEmoji? = emojis.firstOrNull { it.publicId == publicId }

        override fun delete(id: CustomEmojiId, accountId: AccountId): LocalCustomEmoji? {
            val target = emojis.firstOrNull { it.id == id && it.uploadedBy == accountId } ?: return null
            emojis -= target
            return target
        }
    }

    private companion object {
        val UPLOADER = AccountId(1)
    }
}
