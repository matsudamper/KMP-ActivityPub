package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.Note
import net.matsudamper.kmp.activitypub.repository.RemoteActorProfileRecord
import net.matsudamper.kmp.activitypub.repository.RemoteActorRecord
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

    /**
     * 固定した投稿はどの経路でも取りに行くので、[requests] とは分けて数える
     */
    private val featuredRequests = mutableListOf<String>()
    private var featured: suspend () -> List<ProfilePost> = { listOf() }
    private var outbox: suspend (pageId: String?) -> RemoteProfilePostPage = {
        RemoteProfilePostPage(posts = listOf(remotePost("<p>最新の投稿</p>", "https://remote.example/notes/1", pinned = false)), nextPageId = null)
    }
    private val remoteProfiles = object : RemoteUserProfiles {
        override suspend fun find(username: String, host: String): RemoteUserProfile {
            requests.add("profile")
            return RemoteUserProfile(RemoteUser(actorUri, username, "Bob", null, listOf()), "https://remote.example/header.png", "自己紹介")
        }

        override suspend fun recentPostsPage(actorUri: String, pageId: String?): RemoteProfilePostPage {
            requests.add("posts")
            return outbox(pageId)
        }

        override suspend fun featuredPosts(actorUri: String): List<ProfilePost> {
            featuredRequests.add(actorUri)
            return featured()
        }

        override suspend fun findByActorUri(actorUri: String): RemoteUserProfile {
            requests.add("profile:$actorUri")
            return RemoteUserProfile(RemoteUser(actorUri, "bob", "Bob", null, listOf()), "https://remote.example/header.png", "自己紹介")
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
        assertEquals("https://remote.example/header.png", profile.headerUrl)
        assertEquals("<p>最新の投稿</p>", profile.posts.single().contentHtml)
        assertEquals(false, profile.following)
    }

    @Test
    fun followedUserHeaderComesFromStoredActor() = runBlocking {
        repositories.remoteActorRepository.save(
            RemoteActorRecord(actorUri, "$actorUri/inbox", null, "key", RemoteActorProfileRecord("bob", "Bob", null, null, "https://remote.example/stored-header.png", listOf())),
            Instant.EPOCH,
        )
        following.setFollowing(viewer, actorUri, true)

        val profile = assertNotNull(service.profile("bob@remote.example", viewer))

        assertEquals(listOf("posts"), requests)
        assertEquals("https://remote.example/stored-header.png", profile.headerUrl)
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
    fun followedUserFallsBackToStoredPostsWhenOutboxFails() = runBlocking {
        outbox = { error("相手のサーバーに接続できない") }
        repositories.timelineRepository.actorPosts.add(storedPost("<p>受け取った投稿</p>", summary = null, sensitive = false))
        repositories.timelineRepository.actorPosts.add(storedPost("<p>閲覧注意の本文</p>", summary = "注意書き", sensitive = true))
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "保存済みの Bob", null, listOf(), Instant.EPOCH))
        following.setFollowing(viewer, actorUri, true)
        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf("posts"), requests)
        assertNull(profile.postsNextCursor)
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
    fun canonicalActorFollowingStillLoadsOutbox() = runBlocking {
        following.setFollowing(viewer, actorUri, true)
        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf("profile", "posts"), requests)
        assertEquals(true, profile.following)
        assertEquals(listOf("<p>最新の投稿</p>"), profile.posts.map { it.contentHtml })
    }

    @Test
    fun unsavedOutboxPostCanBeLikedButNotOpened() = runBlocking {
        val post = assertNotNull(service.profile("bob@remote.example", viewer)).posts.single()

        assertEquals(ProfilePostLike(postId = UnsavedRemoteNoteLikeId.of("https://remote.example/notes/1"), likedByMe = false), post.like)
        assertNull(post.postId)
        assertNull(post.linkPreviewId)
    }

    @Test
    fun savedPostOfUnfollowedUserKeepsLikeButCannotBeOpened() = runBlocking {
        repositories.timelineRepository.actorPosts.add(storedPost("<p>受け取った投稿</p>", summary = null, sensitive = false).copy(likedByMe = true))

        val post = assertNotNull(service.profile("bob@remote.example", viewer)).posts.single()

        assertEquals(ProfilePostLike(postId = "r1", likedByMe = true), post.like)
        assertNull(post.postId)
    }

    @Test
    fun followedUserPostsMixOutboxAndStoredPostsAndContinueFromOutboxPage() = runBlocking {
        outbox = { pageId ->
            when (pageId) {
                null -> RemoteProfilePostPage(
                    posts = listOf(
                        remotePost("<p>3</p>", "https://remote.example/notes/3", pinned = false, publishedAt = Instant.ofEpochSecond(30)),
                        remotePost("<p>2</p>", "https://remote.example/notes/2", pinned = false, publishedAt = Instant.ofEpochSecond(20)),
                    ),
                    nextPageId = "page-2",
                )

                "page-2" -> RemoteProfilePostPage(
                    posts = listOf(remotePost("<p>相手のサーバーの 1</p>", "https://remote.example/notes/1", pinned = false, publishedAt = Instant.ofEpochSecond(10))),
                    nextPageId = null,
                )

                else -> error("知らないページ: $pageId")
            }
        }
        // outbox に載らないフォロワー限定の投稿
        repositories.timelineRepository.actorPosts.add(
            storedPost("<p>フォロワー限定</p>", summary = null, sensitive = false).copy(
                position = TimelinePosition(25_000, "r9"),
                noteRef = TimelineNoteRef.Remote("https://remote.example/notes/9"),
            ),
        )
        repositories.timelineRepository.actorPosts.add(
            storedPost("<p>保存済みの 1</p>", summary = null, sensitive = false).copy(position = TimelinePosition(10_000, "r1")),
        )
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "Bob", null, listOf(), Instant.EPOCH))
        following.setFollowing(viewer, actorUri, true)

        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf("<p>3</p>", "<p>フォロワー限定</p>", "<p>2</p>"), profile.posts.map { it.contentHtml })
        val cursor = assertNotNull(profile.postsNextCursor)
        assertEquals(cursor, ProfilePostCursor.decode(cursor.encode()))

        val next = assertIs<RemoteProfilePostsResult.Found>(service.remotePosts(viewer, actorUri, cursor)).page
        assertEquals(listOf("<p>保存済みの 1</p>"), next.posts.map { it.contentHtml })
        assertEquals(listOf("r1"), next.posts.map { it.postId })
        assertNull(next.nextCursor)
    }

    @Test
    fun remotePostsNeedLoginAndReportUnreachableOutbox() = runBlocking {
        val cursor = ProfilePostCursor(nextPageId = "page-2", before = null)
        assertEquals(RemoteProfilePostsResult.NotLoggedIn, service.remotePosts(null, actorUri, cursor))

        outbox = { error("相手のサーバーに接続できない") }
        assertEquals(RemoteProfilePostsResult.Unreachable, service.remotePosts(viewer, actorUri, cursor))
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

    @Test
    fun unfollowedUserFeaturedPostsComeFirstWithoutDuplicates() = runBlocking {
        featured = {
            listOf(
                remotePost("<p>固定した投稿</p>", "https://remote.example/notes/pinned", pinned = true),
                remotePost("<p>最新の投稿</p>", "https://remote.example/notes/1", pinned = true),
            )
        }

        val posts = assertNotNull(service.profile("bob@remote.example", viewer)).posts

        assertEquals(listOf(actorUri), featuredRequests)
        assertEquals(listOf("<p>固定した投稿</p>", "<p>最新の投稿</p>"), posts.map { it.contentHtml })
        assertEquals(listOf(true, true), posts.map { it.pinned })
    }

    @Test
    fun followedUserFeaturedPostUsesStoredPost() = runBlocking {
        repositories.timelineRepository.actorPosts.add(storedPost("<p>受け取った投稿</p>", summary = null, sensitive = false))
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "保存済みの Bob", null, listOf(), Instant.EPOCH))
        following.setFollowing(viewer, actorUri, true)
        featured = { listOf(remotePost("<p>相手のサーバーの本文</p>", "https://remote.example/notes/1", pinned = true)) }

        val post = assertNotNull(service.profile("bob@remote.example", viewer)).posts.single()

        assertEquals("<p>受け取った投稿</p>", post.contentHtml)
        assertEquals("r1", post.postId)
        assertEquals(true, post.pinned)
    }

    @Test
    fun featuredFailureStillShowsProfile() = runBlocking {
        featured = { error("相手のサーバーに接続できない") }

        val posts = assertNotNull(service.profile("bob@remote.example", viewer)).posts

        assertEquals(listOf("<p>最新の投稿</p>"), posts.map { it.contentHtml })
        assertEquals(listOf(false), posts.map { it.pinned })
    }

    @Test
    fun featuredRequestIgnoringCancellationDoesNotHoldProfile() = runBlocking {
        featured = {
            withContext(NonCancellable) { delay(1.minutes) }
            listOf()
        }

        val startedAt = System.nanoTime()
        val posts = assertNotNull(service.profile("bob@remote.example", viewer)).posts

        assertTrue((System.nanoTime() - startedAt).nanoseconds < 30.seconds)
        assertEquals(listOf("<p>最新の投稿</p>"), posts.map { it.contentHtml })
    }

    @Test
    fun slowFeaturedRequestDoesNotHoldProfile() = runBlocking {
        featured = {
            delay(1.minutes)
            listOf(remotePost("<p>固定した投稿</p>", "https://remote.example/notes/pinned", pinned = true))
        }

        val posts = assertNotNull(service.profile("bob@remote.example", viewer)).posts

        assertEquals(listOf("<p>最新の投稿</p>"), posts.map { it.contentHtml })
    }

    private fun remotePost(contentHtml: String, noteUri: String, pinned: Boolean): ProfilePost =
        remotePost(contentHtml = contentHtml, noteUri = noteUri, pinned = pinned, publishedAt = Instant.EPOCH)

    private fun remotePost(contentHtml: String, noteUri: String, pinned: Boolean, publishedAt: Instant): ProfilePost =
        ProfilePost(
            contentHtml = contentHtml,
            publishedAt = publishedAt.toString(),
            summary = null,
            sensitive = false,
            linkPreviewId = null,
            postId = null,
            deletableNoteId = null,
            pinned = pinned,
            pinnableNoteId = null,
            images = listOf(),
            favouriteCount = null,
            stamps = listOf(),
            like = null,
            noteRef = TimelineNoteRef.Remote(noteUri),
            boostCount = null,
            boostedNoteAuthor = null,
            emojis = listOf(),
        )

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
