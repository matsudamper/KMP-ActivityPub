package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.AccountEvents
import net.matsudamper.kmp.activitypub.timeline.FakeTimelineStore
import net.matsudamper.kmp.activitypub.timeline.TimelineService

class ViewablePostServiceTest {
    private val repositories = FakeRepositories()
    private val viewer = AccountId(1)
    private val objectUri = "https://remote.example/notes/1"
    private val storedPost = TimelinePost(
        position = TimelinePosition(1, "r1"),
        contentHtml = "<p>保存した投稿</p>",
        publishedAt = Instant.EPOCH,
        actorUri = "https://remote.example/users/bob",
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
    )
    private val imports = mutableListOf<String>()
    private val unsavedNotes = object : UnsavedRemoteNotes {
        override fun storedMember(viewer: AccountId, objectUri: String): String? =
            repositories.timelineRepository.storedRemotePosts(viewer, setOf(objectUri)).firstOrNull()?.reactionMember

        override suspend fun import(viewer: AccountId, objectUri: String): String {
            imports.add(objectUri)
            repositories.timelineRepository.storedFetchedNotes[objectUri] = storedPost
            return storedPost.position.member
        }

        override suspend fun fetch(objectUri: String): RemoteNoteFetchResult = RemoteNoteFetchResult.NotFound
    }
    private val timeline = TimelineService(
        repositories.timelineRepository,
        repositories.followingGroupRepository,
        BlockLists(repositories.serverBlockRepository, repositories.actorBlockRepository),
        FakeTimelineStore(mutableListOf()),
        AccountEvents(repositories.timelineRepository),
        Clock.systemUTC(),
    )

    @Test
    fun unsavedPostIsStoredWhenOpened() = runBlocking {
        val service = ViewablePostService(timeline, AccountService(repositories.accountRepository), unsavedNotes)

        val post = service.post(viewer, UnsavedRemoteNoteId.of(objectUri))

        assertEquals("r1", post?.post?.position?.member)
        assertEquals(listOf(objectUri), imports)
    }

    @Test
    fun linkPreviewsDoNotStoreUnsavedPost() = runBlocking {
        val service = ViewablePostService(timeline, AccountService(repositories.accountRepository), unsavedNotes)

        assertNull(service.contentHtml(viewer, UnsavedRemoteNoteId.of(objectUri)))
        assertEquals(listOf(), imports)

        repositories.timelineRepository.storedFetchedNotes[objectUri] = storedPost
        assertEquals("<p>保存した投稿</p>", service.contentHtml(viewer, UnsavedRemoteNoteId.of(objectUri)))
    }

    @Test
    fun unsavedPostCannotBeOpenedWithoutFederation() = runBlocking {
        repositories.timelineRepository.storedFetchedNotes[objectUri] = storedPost
        val service = ViewablePostService(timeline, AccountService(repositories.accountRepository), null)

        assertNull(service.post(viewer, UnsavedRemoteNoteId.of(objectUri)))
        assertEquals("r1", service.post(viewer, "r1")?.post?.position?.member)
    }

    @Test
    fun onlyViewersOwnLocalPostIsOwned() = runBlocking {
        val alice = repositories.accountRepository.add("alice", "hash", "type")
        val carol = repositories.accountRepository.add("carol", "hash", "type")
        // FakeTimelineRepository が組み立てるこのサーバーの投稿は alice の投稿になる
        repositories.timelineRepository.positions.add(TimelinePosition(1, "l1"))
        val service = ViewablePostService(timeline, AccountService(repositories.accountRepository), unsavedNotes)

        assertTrue(assertNotNull(service.post(alice, "l1")).ownedByViewer)
        assertFalse(assertNotNull(service.post(carol, "l1")).ownedByViewer)
    }
}
