package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.LikedNotePosition
import net.matsudamper.kmp.activitypub.repository.LikedTimelinePost
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchRecord
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.shared.AccountId

class ProfileLikesServiceTest {
    private val repositories = FakeRepositories()
    private val alice = repositories.accountRepository.add("alice", "hash", "type")
    private val bob = repositories.accountRepository.add("bob", "hash", "type")
    private val actorUri = "https://remote.example/users/carol"
    private var remoteLiked: RemoteLikedObjects = RemoteLikedObjects.Absent
    private val imported = mutableListOf<String>()
    private val unsavedNotes = object : UnsavedRemoteNotes {
        override fun storedMember(viewer: AccountId, objectUri: String): String? = null

        override suspend fun import(viewer: AccountId, objectUri: String): String {
            imported.add(objectUri)
            repositories.timelineRepository.storedFetchedNotes[objectUri] = remotePost(objectUri, "r${imported.size + 10}")
            return "r${imported.size + 10}"
        }

        override suspend fun fetch(objectUri: String): RemoteNoteFetchResult = RemoteNoteFetchResult.NotFound
    }
    private val service = ProfileLikesService(
        accounts = repositories.accountRepository,
        remoteActors = repositories.remoteActorRepository,
        timeline = repositories.timelineRepository,
        remoteProfiles = null,
        remoteLikes = { _, _, _ -> remoteLiked },
        unsavedNotes = unsavedNotes,
        localDomain = "local.example",
    )

    @Test
    fun likesOfAccountThatDoesNotPublishThemAreHiddenFromOthers() = runBlocking {
        assertEquals(ProfileLikesResult.Hidden, service.firstPage("bob", viewer = alice))
    }

    @Test
    fun ownerSeesOwnLikesEvenWhenNotPublished() = runBlocking {
        repositories.timelineRepository.likedPosts.add(liked("https://remote.example/notes/1", "r1"))

        val page = assertIs<ProfileLikesResult.Found>(service.firstPage("bob", viewer = bob)).page

        assertEquals(listOf("r1"), page.posts.map { it.position.member })
    }

    @Test
    fun publishedLikesAreShownToOthersButNotToLoggedOutViewers() = runBlocking {
        repositories.accountRepository.likesPublicUsernames.add("bob")
        repositories.timelineRepository.likedPosts.add(liked("https://remote.example/notes/1", "r1"))

        assertEquals(listOf("r1"), assertIs<ProfileLikesResult.Found>(service.firstPage("bob", viewer = alice)).page.posts.map { it.position.member })
        assertEquals(ProfileLikesResult.Hidden, service.firstPage("bob", viewer = null))
    }

    @Test
    fun remoteUserWithoutLikedCollectionIsHidden() = runBlocking {
        saveRemoteActor()

        assertEquals(ProfileLikesResult.Hidden, service.firstPage("carol@remote.example", viewer = alice))
    }

    @Test
    fun remoteLikesKeepTheOrderOfTheCollectionAndImportUnsavedNotes() = runBlocking {
        saveRemoteActor()
        repositories.timelineRepository.storedFetchedNotes["https://remote.example/notes/stored"] = remotePost("https://remote.example/notes/stored", "r1")
        remoteLiked = RemoteLikedObjects.Found(
            objectUris = listOf("https://other.example/notes/unsaved", "https://remote.example/notes/stored"),
            next = OutboxPosition(pageId = "https://remote.example/users/carol/liked?page=2", itemOffset = 0),
        )

        val page = assertIs<ProfileLikesResult.Found>(service.firstPage("carol@remote.example", viewer = alice)).page

        assertEquals(listOf("https://other.example/notes/unsaved"), imported)
        assertEquals(
            listOf("https://other.example/notes/unsaved", "https://remote.example/notes/stored"),
            page.posts.map { (it.noteRef as TimelineNoteRef.Remote).objectUri },
        )
        assertEquals(
            ProfileLikeCursor.Remote(actorUri = actorUri, liked = OutboxPosition(pageId = "https://remote.example/users/carol/liked?page=2", itemOffset = 0)),
            page.nextCursor,
        )
    }

    @Test
    fun unreadableNextPageOfRemoteLikesIsReported() = runBlocking {
        remoteLiked = RemoteLikedObjects.Unavailable
        val cursor = ProfileLikeCursor.Remote(actorUri = actorUri, liked = OutboxPosition(pageId = "https://remote.example/liked?page=2", itemOffset = 0))

        assertEquals(ProfileLikesResult.Unreachable, service.morePage(viewer = alice, cursor = cursor))
    }

    private fun saveRemoteActor() {
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "carol", "Carol", null, listOf(), Instant.EPOCH))
    }

    private fun liked(objectUri: String, member: String): LikedTimelinePost =
        LikedTimelinePost(post = remotePost(objectUri, member), position = LikedNotePosition(likedAt = Instant.EPOCH, member = member))

    private fun remotePost(objectUri: String, member: String): TimelinePost =
        TimelinePost(
            position = TimelinePosition(1, member),
            contentHtml = "<p>投稿</p>",
            publishedAt = Instant.EPOCH,
            actorUri = actorUri,
            username = "carol",
            displayName = "Carol",
            summary = null,
            sensitive = false,
            actorIconUrl = null,
            actorUpdatedAt = null,
            images = listOf(),
            favouriteCount = null,
            stamps = listOf(),
            likedByMe = false,
            boostedByMe = null,
            reactionMember = member,
            noteRef = TimelineNoteRef.Remote(objectUri),
            boostCount = null,
            boostedBy = null,
            actorEmojis = listOf(),
            emojis = listOf(),
            deleted = false,
            boostUndone = false,
            inReplyTo = null,
        )
}
