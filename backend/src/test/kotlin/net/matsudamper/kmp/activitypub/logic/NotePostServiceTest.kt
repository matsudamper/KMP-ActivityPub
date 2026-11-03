package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.FakeDeliveryQueueRepository
import net.matsudamper.kmp.activitypub.FakeFollowerRepository
import net.matsudamper.kmp.activitypub.FakeNoteImageRepository
import net.matsudamper.kmp.activitypub.FakeNoteRepository
import net.matsudamper.kmp.activitypub.federation.RepositoryNoteStore
import net.matsudamper.kmp.activitypub.repository.NewNoteImage
import net.matsudamper.kmp.activitypub.repository.NoteImageId
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.note.NotePublisher

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class NotePostServiceTest {
    private val followerRepository = FakeFollowerRepository()
    private val noteRepository = FakeNoteRepository()
    private val noteImageRepository = FakeNoteImageRepository()
    private val deliveryQueueRepository = FakeDeliveryQueueRepository(noteRepository, noteImageRepository)
    private val noteImageUrls = NoteImageUrls(DOMAIN)
    private var enqueuedCount = 0
    private val timelinePosts = mutableListOf<PublicNoteId>()

    private val service =
        NotePostService(
            directory = ActorDirectory(domain = DOMAIN, stored = OnlyUser1),
            noteRepository = noteRepository,
            noteImageRepository = noteImageRepository,
            noteImageUrls = noteImageUrls,
            publisher = NotePublisher(RepositoryNoteStore(noteRepository = noteRepository, noteImageUrls = noteImageUrls), null),
            followerRepository = followerRepository,
            deliveryQueueRepository = deliveryQueueRepository,
            onEnqueued = { enqueuedCount++ },
            onPosted = { timelinePosts.add(it) },
        )

    @Test
    fun `投稿すると本文を HTML にしてフォロワーの inbox ごとに積む`() {
        followerRepository.deliveryInboxes += listOf(INBOX_A, INBOX_B)

        val result = service.post(accountId = USER1_ID, username = "user1", body = "<b>hello</b>\nworld", requestId = REQUEST_ID, imageIds = listOf())

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
        assertEquals(PostNoteResult.EmptyBody, service.post(accountId = USER1_ID, username = "user1", body = " \n ", requestId = REQUEST_ID, imageIds = listOf()))

        assertEquals(listOf(), deliveryQueueRepository.posts)
        assertEquals(0, enqueuedCount)
    }

    @Test
    fun `長すぎる本文は積まない`() {
        assertEquals(PostNoteResult.TooLong, service.post(accountId = USER1_ID, username = "user1", body = "あ".repeat(NoteComposer.MAX_LENGTH + 1), requestId = REQUEST_ID, imageIds = listOf()))

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `アクターとして扱えないアカウントでは積まない`() {
        assertEquals(PostNoteResult.AccountNotFound, service.post(accountId = OTHER_ID, username = "other", body = "hello", requestId = REQUEST_ID, imageIds = listOf()))

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `同じ requestId で送り直されたら最初の投稿の URL を返して積み直さない`() {
        followerRepository.deliveryInboxes += listOf(INBOX_A)

        val first = assertIs<PostNoteResult.Posted>(service.post(accountId = USER1_ID, username = "user1", body = "hello", requestId = REQUEST_ID, imageIds = listOf()))
        val resent = assertIs<PostNoteResult.Posted>(service.post(accountId = USER1_ID, username = "user1", body = "hello", requestId = REQUEST_ID, imageIds = listOf()))

        assertEquals(first, resent)
        assertEquals(1, deliveryQueueRepository.posts.size)
        assertEquals(1, enqueuedCount)
        assertEquals(1, timelinePosts.size)
    }

    @Test
    fun `置いた画像を添えて配り、相手が取りに来る URL を載せる`() {
        followerRepository.deliveryInboxes += listOf(INBOX_A)
        val imageId = addImage(USER1_ID)

        val result = service.post(accountId = USER1_ID, username = "user1", body = "猫", requestId = REQUEST_ID, imageIds = listOf(imageId))

        assertIs<PostNoteResult.Posted>(result)
        val post = deliveryQueueRepository.posts.single()
        assertEquals(listOf(imageId), post.imageIds)
        assertTrue(post.body.contains("https://$DOMAIN/media/users/1/note-image/a"))
        assertEquals(listOf(imageId), noteRepository.notes.single().images.map { it.id })
    }

    @Test
    fun `画像があれば本文が空でも積む`() {
        val imageId = addImage(USER1_ID)

        val result = service.post(accountId = USER1_ID, username = "user1", body = " ", requestId = REQUEST_ID, imageIds = listOf(imageId))

        assertIs<PostNoteResult.Posted>(result)
        assertEquals("", deliveryQueueRepository.posts.single().contentHtml)
    }

    @Test
    fun `別のアカウントの画像は添えない`() {
        val imageId = addImage(OTHER_ID)

        val result = service.post(accountId = USER1_ID, username = "user1", body = "猫", requestId = REQUEST_ID, imageIds = listOf(imageId))

        assertEquals(PostNoteResult.ImageNotFound, result)
        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `別の投稿に添えた画像は添えない`() {
        val imageId = addImage(USER1_ID)
        assertIs<PostNoteResult.Posted>(
            service.post(accountId = USER1_ID, username = "user1", body = "1", requestId = REQUEST_ID, imageIds = listOf(imageId)),
        )

        val result = service.post(accountId = USER1_ID, username = "user1", body = "2", requestId = "request-2", imageIds = listOf(imageId))

        assertEquals(PostNoteResult.ImageNotFound, result)
        assertEquals(1, deliveryQueueRepository.posts.size)
    }

    @Test
    fun `画像を添えた投稿を同じ requestId で送り直したら最初の投稿を返す`() {
        val imageId = addImage(USER1_ID)

        val first = service.post(accountId = USER1_ID, username = "user1", body = "猫", requestId = REQUEST_ID, imageIds = listOf(imageId))
        val resent = service.post(accountId = USER1_ID, username = "user1", body = "猫", requestId = REQUEST_ID, imageIds = listOf(imageId))

        assertEquals(assertIs<PostNoteResult.Posted>(first), resent)
        assertEquals(1, deliveryQueueRepository.posts.size)
    }

    @Test
    fun `画像は 4 枚まで`() {
        val imageIds = List(NotePostService.MAX_IMAGES + 1) { addImage(USER1_ID, fileId = "f$it") }

        val result = service.post(accountId = USER1_ID, username = "user1", body = "猫", requestId = REQUEST_ID, imageIds = imageIds)

        assertEquals(PostNoteResult.TooManyImages, result)
    }

    private fun addImage(
        accountId: AccountId,
        fileId: String = "a",
    ): NoteImageId {
        val id = NoteImageId("image-${accountId.value}-$fileId")
        noteImageRepository.add(
            NewNoteImage(
                accountId = accountId,
                id = id,
                path = "users/${accountId.value}/note-image/$fileId",
                mediaType = "image/webp",
                description = null,
                width = 10,
                height = 10,
                uploadedAt = Instant.EPOCH,
            ),
        )
        return id
    }

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
        val USER1_ID = AccountId(1)
        val OTHER_ID = AccountId(2)
    }
}
