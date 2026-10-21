package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.FakeDeliveryQueueRepository
import net.matsudamper.kmp.activitypub.FakeFollowerRepository
import net.matsudamper.kmp.activitypub.FakeHomeTimelineStore
import net.matsudamper.kmp.activitypub.FakeNoteRepository
import net.matsudamper.kmp.activitypub.federation.RepositoryNoteStore
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.NotePosition
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.HomeTimelineService
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.StoredActorNames
import net.matsudamper.kotpub.note.NotePublisher

@Suppress("RemoveRedundantBackticks", "NonAsciiCharacters")
class NotePostServiceTest {
    private val followerRepository = FakeFollowerRepository()
    private val noteRepository = FakeNoteRepository()
    private val deliveryQueueRepository = FakeDeliveryQueueRepository(noteRepository)
    private val homeTimelineStore = FakeHomeTimelineStore()
    private var enqueuedCount = 0

    private val service =
        NotePostService(
            directory = ActorDirectory(domain = DOMAIN, stored = OnlyUser1),
            publisher = NotePublisher(RepositoryNoteStore(noteRepository), null),
            followerRepository = followerRepository,
            deliveryQueueRepository = deliveryQueueRepository,
            homeTimelineService = HomeTimelineService(store = homeTimelineStore, noteRepository = noteRepository, maxSize = 10),
            onEnqueued = { enqueuedCount++ },
        )

    @Test
    fun `投稿すると本文を HTML にしてフォロワーの inbox ごとに積む`() {
        followerRepository.deliveryInboxes += listOf(INBOX_A, INBOX_B)

        val result = service.post(account = USER1, body = "<b>hello</b>\nworld", requestId = REQUEST_ID)

        val posted = assertIs<PostNoteResult.Posted>(result)
        assertTrue(posted.url.startsWith("https://$DOMAIN/"))
        val post = deliveryQueueRepository.posts.single()
        assertEquals("user1", post.username)
        assertEquals(posted.publicId, post.publicId)
        assertEquals("<p>&lt;b&gt;hello&lt;/b&gt;<br>world</p>", post.contentHtml)
        assertEquals(listOf(INBOX_A, INBOX_B), post.inboxes)
        assertTrue(post.body.contains("\"Create\""))
        assertEquals(1, enqueuedCount)
    }

    @Test
    fun `投稿すると自分のホームタイムラインに並ぶ`() {
        val posted = assertIs<PostNoteResult.Posted>(service.post(account = USER1, body = "hello", requestId = REQUEST_ID))

        val note = noteRepository.notes.single()
        assertEquals(posted.publicId, note.publicId)
        assertEquals(listOf(NotePosition(publishedAt = note.publishedAt, publicId = note.publicId)), homeTimelineStore.timelines[USER1.id])
    }

    @Test
    fun `空の本文はホームタイムラインに並べない`() {
        service.post(account = USER1, body = " ", requestId = REQUEST_ID)

        assertEquals(mapOf(), homeTimelineStore.timelines)
    }

    @Test
    fun `空の本文は積まない`() {
        assertEquals(PostNoteResult.EmptyBody, service.post(account = USER1, body = " \n ", requestId = REQUEST_ID))

        assertEquals(listOf(), deliveryQueueRepository.posts)
        assertEquals(0, enqueuedCount)
    }

    @Test
    fun `長すぎる本文は積まない`() {
        assertEquals(PostNoteResult.TooLong, service.post(account = USER1, body = "あ".repeat(NoteComposer.MAX_LENGTH + 1), requestId = REQUEST_ID))

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `アクターとして扱えないアカウントでは積まない`() {
        assertEquals(PostNoteResult.AccountNotFound, service.post(account = OTHER, body = "hello", requestId = REQUEST_ID))

        assertEquals(listOf(), deliveryQueueRepository.posts)
    }

    @Test
    fun `同じ requestId で送り直されたら最初の投稿の URL を返して積み直さない`() {
        followerRepository.deliveryInboxes += listOf(INBOX_A)

        val first = assertIs<PostNoteResult.Posted>(service.post(account = USER1, body = "hello", requestId = REQUEST_ID))
        val resent = assertIs<PostNoteResult.Posted>(service.post(account = USER1, body = "hello", requestId = REQUEST_ID))

        assertEquals(first, resent)
        assertEquals(1, deliveryQueueRepository.posts.size)
        assertEquals(1, enqueuedCount)
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
        val USER1 = Account(id = AccountId(1), username = "user1", createdAt = Instant.EPOCH)
        val OTHER = Account(id = AccountId(2), username = "other", createdAt = Instant.EPOCH)
    }
}
