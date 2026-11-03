package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import net.matsudamper.kmp.activitypub.FakeImageStorage
import net.matsudamper.kmp.activitypub.FakeNoteRepository
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.imagestorage.ImageStorage
import net.matsudamper.kmp.activitypub.repository.NoteImage
import net.matsudamper.kmp.activitypub.repository.UploadedNoteImage
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class UploadedNoteImageCleanerTest {
    private val noteRepository = FakeNoteRepository()
    private val imageStorage = FakeImageStorage()
    private val cleaner = UploadedNoteImageCleaner(noteRepository, imageStorage, Clock.fixed(NOW, ZoneOffset.UTC))

    @Test
    fun `期限を過ぎた画像だけを記録と置き場から消す`() {
        val expired = upload(id = "expired", expiresAt = NOW)
        val alive = upload(id = "alive", expiresAt = NOW.plusSeconds(1))

        cleaner.cleanExpired()

        assertEquals(listOf(alive.image.publicId), noteRepository.uploadedImages.map { it.image.publicId })
        assertEquals(setOf(ImagePath(alive.image.path)), imageStorage.images.keys)
        assertEquals(false, imageStorage.images.containsKey(ImagePath(expired.image.path)))
    }

    private fun upload(
        id: String,
        expiresAt: Instant,
    ): UploadedNoteImage {
        val path = ImagePath("users/1/note-images/$id")
        imageStorage.put(
            ImageStorage.PutRequest(
                ownerAccountId = AccountId(1),
                path = path,
                contentType = "image/png",
                maxBytes = 10,
                inputStream = byteArrayOf(1).inputStream(),
            ),
        )
        val uploaded =
            UploadedNoteImage(
                ownerAccountId = AccountId(1),
                image = NoteImage(NoteImagePublicId(id), path = path.value, contentType = "image/png", description = null),
                expiresAt = expiresAt,
            )
        noteRepository.addUploadedImage(uploaded)
        return uploaded
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-01T00:00:00Z")
    }
}
