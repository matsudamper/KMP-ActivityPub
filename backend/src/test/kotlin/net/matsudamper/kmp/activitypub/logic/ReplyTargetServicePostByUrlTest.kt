package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.shared.AccountId

class ReplyTargetServicePostByUrlTest {
    private val repositories = FakeRepositories()
    private val viewer = AccountId(1)
    private val pageUrl = "https://remote.example/@bob/1"
    private val objectUri = "https://remote.example/users/bob/statuses/1"
    private val author = RemoteUser(
        actorUri = "https://remote.example/users/bob",
        username = "bob",
        displayName = null,
        iconUrl = null,
        displayNameEmojis = listOf(),
    )

    private val fetchedNote = RemoteNoteInput(
        objectUri = objectUri,
        actorUri = author.actorUri,
        contentHtml = "<p>投稿</p>",
        language = null,
        visibility = "public",
        localRecipients = setOf(),
        inReplyTo = null,
        quoteUri = null,
        summary = null,
        sensitive = false,
        publishedAt = Instant.EPOCH,
        receivedAt = Instant.EPOCH,
        editedAt = null,
        images = listOf(),
        emojis = listOf(),
        linkHref = null,
        poll = null,
    )

    private val storedPost = TimelinePost(
        position = TimelinePosition(1, "r1"),
        contentHtml = "<p>保存した投稿</p>",
        language = null,
        publishedAt = Instant.EPOCH,
        actorUri = author.actorUri,
        username = "bob",
        displayName = null,
        summary = null,
        sensitive = false,
        actorIconUrl = null,
        actorUpdatedAt = null,
        images = listOf(),
        emojis = listOf(),
        actorEmojis = listOf(),
        favouriteCount = null,
        stamps = listOf(),
        likedByMe = false,
        boostedByMe = false,
        reactionMember = "r1",
        noteRef = TimelineNoteRef.Remote(objectUri),
        boostCount = null,
        boostedBy = null,
        deleted = false,
        boostUndone = false,
        inReplyTo = null,
        quoteUri = null,
        editedAt = null,
        linkHref = null,
    )
    private val fetchedUrls = mutableListOf<String>()
    private val unsavedNotes = object : UnsavedRemoteNotes {
        override fun storedMember(viewer: AccountId, objectUri: String): String? = null

        override suspend fun import(viewer: AccountId, objectUri: String): String? = null

        override suspend fun fetch(objectUri: String): RemoteNoteFetchResult = RemoteNoteFetchResult.NotFound

        override suspend fun fetchByUrl(url: String): RemoteNoteFetchResult {
            fetchedUrls.add(url)
            return if (url == pageUrl) RemoteNoteFetchResult.Found(note = fetchedNote, author = author) else RemoteNoteFetchResult.NotFound
        }
    }
    private val service = ReplyTargetService(
        domain = "local.example",
        timeline = repositories.timelineRepository,
        unsavedNotes = unsavedNotes,
    )

    @Test
    fun unsavedRemotePostIsReturnedWithUnsavedId(): Unit = runBlocking {
        val post = assertIs<ReplyTargetResult.Found>(service.postByUrl(viewer, pageUrl)).post

        assertEquals(UnsavedRemoteNoteId.of(objectUri), post.position.member)
        assertEquals(TimelineNoteRef.Remote(objectUri), post.noteRef)
        assertEquals(listOf(pageUrl), fetchedUrls)
    }

    @Test
    fun storedPostIsReturnedWhenPageUrlPointsToIt(): Unit = runBlocking {
        repositories.timelineRepository.storedFetchedNotes[objectUri] = storedPost

        val post = assertIs<ReplyTargetResult.Found>(service.postByUrl(viewer, pageUrl)).post

        assertEquals("r1", post.position.member)
    }

    @Test
    fun storedPostIsReturnedWithoutFetchingWhenUrlIsItsId(): Unit = runBlocking {
        repositories.timelineRepository.storedFetchedNotes[objectUri] = storedPost

        val post = assertIs<ReplyTargetResult.Found>(service.postByUrl(viewer, objectUri)).post

        assertEquals("r1", post.position.member)
        assertEquals(listOf(), fetchedUrls)
    }

    @Test
    fun plainHttpUrlIsNotFetched(): Unit = runBlocking {
        assertIs<ReplyTargetResult.NotFound>(service.postByUrl(viewer, "http://remote.example/@bob/1"))
        assertEquals(listOf(), fetchedUrls)
    }
}
