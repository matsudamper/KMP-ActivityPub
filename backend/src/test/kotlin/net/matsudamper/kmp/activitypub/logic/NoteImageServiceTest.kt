package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import net.matsudamper.kmp.activitypub.FakeImageStorage
import net.matsudamper.kmp.activitypub.FakeNoteImageRepository
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.shared.AccountId

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class NoteImageServiceTest {
    private val imageStorage = FakeImageStorage()
    private val noteImageRepository = FakeNoteImageRepository()
    private val service =
        NoteImageService(
            imageStorage = imageStorage,
            noteImageRepository = noteImageRepository,
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )

    @Test
    fun `置いた画像は送られた種類ではなく中身の形式と寸法で記録する`() {
        val result = service.upload(accountId = ACCOUNT_ID, image = TestProfileIcons.read("13x406-lossless.webp"), description = "  猫  ")

        val uploaded = assertIs<UploadNoteImageResult.Uploaded>(result)
        val added = noteImageRepository.added.single()
        assertEquals(uploaded.id, added.id)
        assertEquals("image/webp", added.mediaType)
        assertEquals(13, added.width)
        assertEquals(406, added.height)
        assertEquals("猫", added.description)
        assertEquals("image/webp", imageStorage.images.getValue(ImagePath(added.path)).contentType)
    }

    @Test
    fun `置いた場所は配信の URL から組み立て直せる`() {
        service.upload(accountId = ACCOUNT_ID, image = TestProfileIcons.read("400x400.png"), description = null)
        val path = noteImageRepository.added.single().path
        val fileId = path.substringAfterLast('/')

        assertEquals(path, NoteImagePaths.parse(accountId = "7", fileId = fileId)?.path?.value)
        assertNull(NoteImagePaths.parse(accountId = "7", fileId = "../profile-icon/x"))
        assertNull(NoteImagePaths.parse(accountId = "x", fileId = fileId))
    }

    @Test
    fun `画像でなければ置かない`() {
        val result = service.upload(accountId = ACCOUNT_ID, image = "<svg></svg>".encodeToByteArray(), description = null)

        assertEquals(UploadNoteImageResult.Unsupported, result)
        assertEquals(mapOf(), imageStorage.images)
    }

    @Test
    fun `大きすぎる画像は置かない`() {
        val image = TestProfileIcons.read("400x400.png") + ByteArray(NoteImageService.MAX_BYTES)

        assertEquals(UploadNoteImageResult.TooLarge, service.upload(accountId = ACCOUNT_ID, image = image, description = null))
        assertEquals(mapOf(), imageStorage.images)
    }

    @Test
    fun `長すぎる代替テキストは断る`() {
        val description = "あ".repeat(NoteImageService.MAX_DESCRIPTION_LENGTH + 1)

        val result = service.upload(accountId = ACCOUNT_ID, image = TestProfileIcons.read("400x400.png"), description = description)

        assertEquals(UploadNoteImageResult.DescriptionTooLong, result)
    }

    private companion object {
        val ACCOUNT_ID = AccountId(7)
    }
}
