package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.matsudamper.kmp.activitypub.FakeAccountRepository
import net.matsudamper.kmp.activitypub.FakeDeliveryQueueRepository
import net.matsudamper.kmp.activitypub.FakeFollowerRepository
import net.matsudamper.kmp.activitypub.FakeImageStorage
import net.matsudamper.kmp.activitypub.FakeNoteRepository
import net.matsudamper.kmp.activitypub.federation.RepositoryNoteStore
import net.matsudamper.kmp.activitypub.imagestorage.ImagePath
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.json.AppJson
import net.matsudamper.kotpub.note.NotePublisher

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class NotePostServiceTest {
    private val followerRepository = FakeFollowerRepository()
    private val noteRepository = FakeNoteRepository()
    private val deliveryQueueRepository = FakeDeliveryQueueRepository(noteRepository)
    private var enqueuedCount = 0
    private val timelinePosts = mutableListOf<PublicNoteId>()
    private val imageStorage = FakeImageStorage()
    private var now: Instant = NOW
    private val imageUrls = NoteImageUrls(DOMAIN)
    private val accountRepository = FakeAccountRepository(hasPasskey = { false }).apply { add(username = "user1", passwordHash = "hash", passwordHashType = "type") }

    private val service =
        NotePostService(
            directory = ActorDirectory(domain = DOMAIN, stored = OnlyUser1),
            publisher = NotePublisher(RepositoryNoteStore(noteRepository, imageUrls), null),
            accountService = AccountService(accountRepository),
            noteRepository = noteRepository,
            imageStorage = imageStorage,
            imageUrls = imageUrls,
            followerRepository = followerRepository,
            deliveryQueueRepository = deliveryQueueRepository,
            clock = object : Clock() {
                override fun getZone(): ZoneId = ZoneOffset.UTC

                override fun withZone(zone: ZoneId?): Clock = this

                override fun instant(): Instant = now
            },
            onEnqueued = { enqueuedCount++ },
            onPosted = { timelinePosts.add(it) },
        )

    @Test
    fun `投稿すると本文を HTML にしてフォロワーの inbox ごとに積む`() {
        followerRepository.deliveryInboxes += listOf(INBOX_A, INBOX_B)

        val result = service.post(username = "user1", body = "<b>hello</b>\nworld", requestId = REQUEST_ID, imageIds = listOf())

        val posted = assertIs<PostNoteResult.Posted>(result)
        assertTrue(posted.url.startsWith("https://$DOMAIN/"))
        val post = deliveryQueueRepository.posts.single()
        assertEquals("user1", post.username)
        assertEquals(posted.publicId, post.publicId)
        assertEquals("<p>&lt;b&gt;hello&lt;/b&gt;<br>world</p>", post.contentHtml)
        assertEquals(listOf(INBOX_A, INBOX_B), post.inboxes)
        assertTrue(post.body.contains("\"Create\""))
        assertEquals(1, enqueuedCount)
        assertEquals(1, timelinePosts.size)
    }

    @Test
    fun `空の本文は積まない`() {
        assertEquals(PostNoteResult.EmptyBody, service.post(username = "user1", body = " \n ", requestId = REQUEST_ID, imageIds = listOf()))

        assertEquals(listOf(), deliveryQueueRepository.posts)
        assertEquals(0, enqueuedCount)
    }

    @Test
    fun `長すぎる本文は積まない`() {
        assertEquals(PostNoteResult.TooLong, service.post(username = "user1", body = "あ".repeat(NoteComposer.MAX_LENGTH + 1), requestId = REQUEST_ID, imageIds = listOf()))

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `アクターとして扱えないアカウントでは積まない`() {
        assertEquals(PostNoteResult.AccountNotFound, service.post(username = "other", body = "hello", requestId = REQUEST_ID, imageIds = listOf()))

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `同じ requestId で送り直されたら最初の投稿の URL を返して積み直さない`() {
        followerRepository.deliveryInboxes += listOf(INBOX_A)

        val first = assertIs<PostNoteResult.Posted>(service.post(username = "user1", body = "hello", requestId = REQUEST_ID, imageIds = listOf()))
        val resent = assertIs<PostNoteResult.Posted>(service.post(username = "user1", body = "hello", requestId = REQUEST_ID, imageIds = listOf()))

        assertEquals(first, resent)
        assertEquals(1, deliveryQueueRepository.posts.size)
        assertEquals(1, enqueuedCount)
        assertEquals(1, timelinePosts.size)
    }

    @Test
    fun `置いた画像を付けた順に記録し、配る Note に自分のサーバーの URL で載せる`() {
        val png = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, description = " 猫 ")))
        val jpeg = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(JPEG, description = " ")))

        assertIs<PostNoteResult.Posted>(service.post(username = "user1", body = "画像", requestId = REQUEST_ID, imageIds = listOf(png, jpeg)))

        val images = deliveryQueueRepository.posts.single().images
        assertEquals(listOf(png, jpeg), images.map { it.publicId })
        assertEquals(listOf("image/png", "image/jpeg"), images.map { it.contentType })
        assertEquals(listOf("猫", null), images.map { it.description })
        assertEquals(listOf(PNG.toList(), JPEG.toList()), images.map { imageStorage.images.getValue(ImagePath(it.path)).bytes.toList() })
        assertTrue(images.all { it.path.startsWith("users/1/note-images/") })
        assertEquals(listOf(), noteRepository.uploadedImages)

        val attachment = (AppJson.parseToJsonElement(deliveryQueueRepository.posts.single().body) as JsonObject)
            .getValue("object").jsonObject.getValue("attachment").jsonArray
        assertEquals(images.map { imageUrls.url(it.publicId) }, attachment.map { it.jsonObject.getValue("url").jsonPrimitive.content })
        assertTrue(images.all { imageUrls.url(it.publicId).startsWith("https://$DOMAIN/media/") })
    }

    @Test
    fun `置いた画像には期限を付ける`() {
        uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null)))

        assertEquals(NOW.plus(NotePostService.UPLOADED_IMAGE_LIFETIME), noteRepository.uploadedImages.single().expiresAt)
    }

    @Test
    fun `画像があれば本文は空でもよい`() {
        val image = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null)))

        assertIs<PostNoteResult.Posted>(service.post(username = "user1", body = " ", requestId = REQUEST_ID, imageIds = listOf(image)))

        assertEquals("", deliveryQueueRepository.posts.single().contentHtml)
    }

    @Test
    fun `画像が多すぎたら積まない`() {
        val images = List(NotePostService.MAX_IMAGES + 1) { uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null))) }

        assertEquals(PostNoteResult.TooManyImages, service.post(username = "user1", body = "hello", requestId = REQUEST_ID, imageIds = images))

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `置いていないか期限を過ぎた画像は付けられない`() {
        val image = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null)))
        now = NOW.plus(NotePostService.UPLOADED_IMAGE_LIFETIME)

        assertEquals(PostNoteResult.ImageNotFound, service.post(username = "user1", body = "hello", requestId = REQUEST_ID, imageIds = listOf(image)))
        assertEquals(
            PostNoteResult.ImageNotFound,
            service.post(username = "user1", body = "hello", requestId = REQUEST_ID, imageIds = listOf(NoteImagePublicId("missing"))),
        )

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `画像として読めないものや大きすぎるもの、長すぎる説明文は置かない`() {
        assertEquals(UploadNoteImageResult.UnsupportedImage, service.uploadImage(username = "user1", image = noteImageUpload("<svg/>".encodeToByteArray(), null)))
        assertEquals(UploadNoteImageResult.TooLarge, service.uploadImage(username = "user1", image = noteImageUpload(PNG + ByteArray(NotePostService.MAX_IMAGE_BYTES), null)))
        val longDescription = "あ".repeat(NotePostService.MAX_DESCRIPTION_LENGTH + 1)
        assertEquals(UploadNoteImageResult.DescriptionTooLong, service.uploadImage(username = "user1", image = noteImageUpload(PNG, longDescription)))

        assertTrue(imageStorage.images.isEmpty())
        assertEquals(listOf(), noteRepository.uploadedImages)
    }

    @Test
    fun `同じ requestId で送り直されたら、画像が投稿に付いた後でも最初の投稿を返す`() {
        val image = uploaded(service.uploadImage(username = "user1", image = noteImageUpload(PNG, null)))
        val first = service.post(username = "user1", body = "hello", requestId = REQUEST_ID, imageIds = listOf(image))
        val resent = service.post(username = "user1", body = "hello", requestId = REQUEST_ID, imageIds = listOf(image))

        assertEquals(first, resent)
        assertEquals(1, deliveryQueueRepository.posts.size)
    }

    private fun uploaded(result: UploadNoteImageResult): NoteImagePublicId = assertIs<UploadNoteImageResult.Uploaded>(result).publicId

    private object OnlyUser1 : StoredActorNames {
        override fun find(username: String): String? = username.takeIf { it == "user1" }

        override fun finds(usernames: Set<String>): Map<String, String> =
            usernames.mapNotNull { username -> find(username)?.let { username to it } }.toMap()
    }

    private companion object {
        const val DOMAIN = "example.com"
        const val INBOX_A = "https://a.example/inbox"
        const val INBOX_B = "https://b.example/inbox"
        const val REQUEST_ID = "request-1"
        val NOW: Instant = Instant.parse("2026-11-01T00:00:00Z")
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1)
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 2)
    }
}

private fun noteImageUpload(
    bytes: ByteArray,
    description: String?,
): NoteImageUpload = NoteImageUpload(size = bytes.size.toLong(), openStream = { bytes.inputStream() }, description = description)
