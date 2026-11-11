package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchRecord
import net.matsudamper.kmp.activitypub.repository.TimelineBooster
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelinePostImage
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class UserProfileServiceTest {
    private val repositories = FakeRepositories()
    private val viewer = repositories.accountRepository.add("alice", "hash", "type")
    private val actorUri = "https://remote.example/users/bob"
    private val following = FollowingService(repositories.accountRepository, repositories.followingRepository, NoOpFollowDelivery, TimelineInvalidator {}, "local.example", Clock.systemUTC())
    private val requests = mutableListOf<String>()
    private val remoteProfiles = object : RemoteUserProfiles {
        override suspend fun find(username: String, host: String): RemoteUserProfile {
            requests.add("profile")
            return RemoteUserProfile(RemoteUser(actorUri, username, "Bob", null, listOf()), "自己紹介")
        }

        override suspend fun recentPosts(actorUri: String): List<ProfilePost> {
            requests.add("posts")
            return listOf(
                ProfilePost(
                    contentHtml = "<p>最新の投稿</p>",
                    publishedAt = Instant.EPOCH.toString(),
                    summary = null,
                    sensitive = false,
                    linkPreviewId = null,
                    postId = null,
                    deletableNoteId = null,
                    pinned = false,
                    pinnableNoteId = null,
                    images = listOf(),
                    favouriteCount = null,
                    stamps = listOf(),
                    like = null,
                    noteRef = TimelineNoteRef.Remote("https://remote.example/notes/1"),
                    boostCount = null,
                    boostedNoteAuthor = null,
                    emojis = listOf(),
                ),
            )
        }

        override suspend fun findByActorUri(actorUri: String): RemoteUserProfile {
            requests.add("profile:$actorUri")
            return RemoteUserProfile(RemoteUser(actorUri, "bob", "Bob", null, listOf()), "自己紹介")
        }
    }
    private val service = UserProfileService(
        accounts = repositories.accountRepository,
        remoteActors = repositories.remoteActorRepository,
        notes = repositories.noteRepository,
        favourites = repositories.favouriteRepository,
        stamps = repositories.stampRepository,
        likes = repositories.likeRepository,
        boosts = repositories.boostRepository,
        timeline = repositories.timelineRepository,
        pins = repositories.notePinRepository,
        following = following,
        remoteProfiles = remoteProfiles,
        localDomain = "local.example",
    )

    @Test
    fun unfollowedUserLoadsRemotePosts() = runBlocking {
        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf("profile", "posts"), requests)
        assertEquals("自己紹介", profile.biography)
        assertEquals("<p>最新の投稿</p>", profile.posts.single().contentHtml)
        assertEquals(false, profile.following)
    }

    @Test
    fun storedActorLoadsProfileByUriWithoutResolvingAcct() = runBlocking {
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "保存済みの Bob", null, listOf(), Instant.EPOCH))
        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf("profile:$actorUri", "posts"), requests)
        assertEquals(actorUri, profile.user.actorUri)
        assertEquals("自己紹介", profile.biography)
        assertEquals("<p>最新の投稿</p>", profile.posts.single().contentHtml)
    }

    @Test
    fun followedUserUsesStoredProfileWithoutRemoteRequests() = runBlocking {
        repositories.timelineRepository.actorPosts.add(storedPost("<p>受け取った投稿</p>", summary = null, sensitive = false))
        repositories.timelineRepository.actorPosts.add(storedPost("<p>閲覧注意の本文</p>", summary = "注意書き", sensitive = true))
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "保存済みの Bob", null, listOf(), Instant.EPOCH))
        following.setFollowing(viewer, actorUri, true)
        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf(), requests)
        assertEquals("保存済みの Bob", profile.user.displayName)
        assertEquals(true, profile.following)
        assertEquals(listOf("<p>受け取った投稿</p>", "<p>閲覧注意の本文</p>"), profile.posts.map { it.contentHtml })
        assertEquals(listOf(null, "注意書き"), profile.posts.map { it.summary })
        assertEquals(listOf(false, true), profile.posts.map { it.sensitive })
        assertEquals(listOf("r1", "r1"), profile.posts.map { it.linkPreviewId })
        assertEquals(listOf("r1", "r1"), profile.posts.map { it.postId })
        assertEquals(listOf(listOf(image), listOf(image)), profile.posts.map { it.images })
    }

    @Test
    fun followedUserBoostsHaveBoostedNoteAuthor() = runBlocking {
        val authorUri = "https://other.example/users/carol"
        repositories.timelineRepository.actorPosts.add(
            storedPost("<p>ブーストされた投稿</p>", summary = null, sensitive = false).copy(
                position = TimelinePosition(2, "b1"),
                actorUri = authorUri,
                username = "carol",
                displayName = "Carol",
                actorIconUrl = "https://other.example/carol.png",
                boostedBy = TimelineBooster(actorUri = actorUri, username = "bob", displayName = "Bob", iconUrl = null, updatedAt = Instant.EPOCH, emojis = listOf()),
            ),
        )
        repositories.timelineRepository.actorPosts.add(storedPost("<p>本人の投稿</p>", summary = null, sensitive = false))
        following.setFollowing(viewer, actorUri, true)
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "Bob", null, listOf(), Instant.EPOCH))

        val posts = assertNotNull(service.profile("bob@remote.example", viewer)).posts
        assertEquals(listOf("b1", "r1"), posts.map { it.postId })
        val author = assertNotNull(posts.first().boostedNoteAuthor)
        assertEquals(authorUri, author.actorUri)
        assertEquals("carol", author.username)
        assertEquals("Carol", author.displayName)
        assertEquals("https://other.example/carol.png", author.iconUrl)
        assertNull(posts.last().boostedNoteAuthor)
    }

    @Test
    fun canonicalActorFollowingPreventsOutboxRequest() = runBlocking {
        following.setFollowing(viewer, actorUri, true)
        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf("profile"), requests)
        assertEquals(true, profile.following)
        assertEquals(listOf(), profile.posts)
    }

    @Test
    fun localProfileUsesStoredPosts() = runBlocking {
        repositories.noteRepository.notes.add(Note(PublicNoteId("note-1"), "alice", "<p>保存済みの投稿</p>", Instant.EPOCH, listOf()))
        val profile = assertNotNull(service.profile("alice", viewer))
        assertEquals(listOf(), requests)
        val post = profile.posts.single()
        assertEquals("<p>保存済みの投稿</p>", post.contentHtml)
        assertEquals("<p>保存済みの投稿</p>", service.localNoteContentHtml(assertNotNull(post.linkPreviewId)))
        assertNull(service.localNoteContentHtml("r1"))
    }

    @Test
    fun ownLocalPostsHaveDeletableNoteIdButOthersDoNot() = runBlocking {
        repositories.accountRepository.add("carol", "hash", "type")
        repositories.noteRepository.notes.add(Note(PublicNoteId("note-alice"), "alice", "<p>自分の投稿</p>", Instant.EPOCH, listOf()))
        repositories.noteRepository.notes.add(Note(PublicNoteId("note-carol"), "carol", "<p>他の人の投稿</p>", Instant.EPOCH, listOf()))

        assertEquals("note-alice", assertNotNull(service.profile("alice", viewer)).posts.single().deletableNoteId)
        assertNull(assertNotNull(service.profile("carol", viewer)).posts.single().deletableNoteId)
    }

    @Test
    fun pinnedPostsComeFirstWithoutDuplicates() = runBlocking {
        repositories.noteRepository.notes.add(Note(PublicNoteId("old"), "alice", "<p>古い投稿</p>", Instant.EPOCH, listOf()))
        repositories.noteRepository.notes.add(Note(PublicNoteId("new"), "alice", "<p>新しい投稿</p>", Instant.EPOCH.plusSeconds(1), listOf()))
        repositories.notePinRepository.pin(viewer, PublicNoteId("old"), Instant.EPOCH, NotePinService.MAX_PINS)

        val posts = assertNotNull(service.profile("alice", viewer)).posts

        assertEquals(listOf("<p>古い投稿</p>", "<p>新しい投稿</p>"), posts.map { it.contentHtml })
        assertEquals(listOf(true, false), posts.map { it.pinned })
    }

    @Test
    fun onlyOwnProfilePostsHavePinnableNoteId() = runBlocking {
        val carol = repositories.accountRepository.add("carol", "hash", "type")
        repositories.noteRepository.notes.add(Note(PublicNoteId("note-carol"), "carol", "<p>他の人の投稿</p>", Instant.EPOCH, listOf()))

        assertEquals(listOf(null), assertNotNull(service.profile("carol", viewer)).posts.map { it.pinnableNoteId })
        assertEquals(listOf(PublicNoteId("note-carol")), assertNotNull(service.profile("carol", carol)).posts.map { it.pinnableNoteId })
    }

    @Test
    fun emptyUsernameDoesNotFetchRemoteProfile() = runBlocking {
        for (acct in listOf("", "@", "@@remote.example")) {
            assertNull(service.profile(acct, viewer))
        }
        assertEquals(listOf(), requests)
    }

    @Test
    fun emptyHostUsesLocalProfile() = runBlocking {
        val profile = assertNotNull(service.profile("alice@", viewer))
        assertEquals("alice", profile.user.username)
        assertEquals("local.example", profile.user.host)
        assertEquals(true, profile.user.isLocal)
        assertEquals(listOf(), requests)
    }

    @Test
    fun anonymousViewerDoesNotFetchRemoteProfile() = runBlocking {
        assertNull(service.profile("bob@remote.example", null))
        assertEquals(listOf(), requests)
    }

    private fun storedPost(contentHtml: String, summary: String?, sensitive: Boolean): TimelinePost =
        TimelinePost(
            position = TimelinePosition(1, "r1"),
            contentHtml = contentHtml,
            publishedAt = Instant.EPOCH,
            actorUri = actorUri,
            username = "bob",
            displayName = "Bob",
            summary = summary,
            sensitive = sensitive,
            actorIconUrl = null,
            actorUpdatedAt = null,
            images = listOf(image),
            favouriteCount = null,
            stamps = listOf(),
            likedByMe = false,
            noteRef = TimelineNoteRef.Remote("https://remote.example/notes/1"),
            boostCount = null,
            boostedBy = null,
            actorEmojis = listOf(),
            emojis = listOf(),
        )

    private companion object {
        val image = TimelinePostImage.Remote(url = "https://remote.example/a.png", description = null)
    }
}
