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
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
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
    private var featured: suspend () -> List<RemoteNoteInput> = { listOf() }
    private var outbox: suspend (from: OutboxPosition?) -> RemoteNotesPage? = {
        RemoteNotesPage(notes = listOf(remoteNote("<p>最新の投稿</p>", "https://remote.example/notes/1")), next = null)
    }
    private val remoteProfiles = object : RemoteUserProfiles {
        override suspend fun find(username: String, host: String): RemoteUserProfile {
            requests.add("profile")
            return RemoteUserProfile(RemoteUser(actorUri, username, "Bob", null, listOf()), "https://remote.example/header.png", "自己紹介")
        }

        override suspend fun recentNotesPage(actorUri: String, from: OutboxPosition?): RemoteNotesPage? {
            requests.add("posts")
            return outbox(from)
        }

        override suspend fun featuredNotes(actorUri: String): List<RemoteNoteInput> {
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
        accountBoosts = repositories.accountBoostRepository,
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
    fun unfollowedUserPostsCanBeLikedAndBoostedOnceStored() = runBlocking {
        repositories.timelineRepository.storedFetchedNotes["https://remote.example/notes/1"] =
            storedPost("<p>保存した投稿</p>", summary = null, sensitive = false).copy(boostedByMe = false)

        val post = assertNotNull(service.profile("bob@remote.example", viewer)).posts.single()

        assertEquals("<p>保存した投稿</p>", post.contentHtml)
        assertEquals("r1", post.reactionPostId)
        assertEquals(ProfilePostLike(postId = "r1", likedByMe = false), post.like)
        assertEquals(false, post.boostedByMe)
        assertNull(post.postId)
    }

    @Test
    fun unsavedPostsAreNotStoredButCanBeLikedAndBoostedLater() = runBlocking {
        val post = assertNotNull(service.profile("bob@remote.example", viewer)).posts.single()

        val unsavedId = UnsavedRemoteNoteId.of("https://remote.example/notes/1")
        assertEquals("<p>最新の投稿</p>", post.contentHtml)
        assertEquals(unsavedId, post.reactionPostId)
        assertEquals(ProfilePostLike(postId = unsavedId, likedByMe = false), post.like)
        assertEquals(false, post.boostedByMe)
        assertNull(post.postId)
        assertTrue(repositories.timelineRepository.storeRequests.isEmpty())
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
    fun followedUserFallsBackToStoredPostsWhenOutboxCannotBeRead() = runBlocking {
        outbox = { null }
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
    fun canonicalActorFollowingStillReadsOutbox() = runBlocking {
        following.setFollowing(viewer, actorUri, true)
        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf("profile", "posts"), requests)
        assertEquals(true, profile.following)
        assertEquals(listOf("<p>最新の投稿</p>"), profile.posts.map { it.contentHtml })
    }

    @Test
    fun followedUserPostsMixOutboxAndStoredPostsAndContinueFromOutbox() = runBlocking {
        outbox = { from ->
            when (from) {
                null -> RemoteNotesPage(
                    notes = listOf(
                        remoteNote("<p>3</p>", "https://remote.example/notes/3", publishedAt = Instant.ofEpochSecond(30)),
                        remoteNote("<p>2</p>", "https://remote.example/notes/2", publishedAt = Instant.ofEpochSecond(20)),
                    ),
                    next = OutboxPosition(pageId = "page-2", itemOffset = 0),
                )

                OutboxPosition(pageId = "page-2", itemOffset = 0) -> RemoteNotesPage(
                    notes = listOf(remoteNote("<p>相手のサーバーの 1</p>", "https://remote.example/notes/1", publishedAt = Instant.ofEpochSecond(10))),
                    next = null,
                )

                else -> error("知らない位置: $from")
            }
        }
        // outbox に載らないフォロワー限定の投稿
        repositories.timelineRepository.actorPosts.add(
            storedPost("<p>フォロワー限定</p>", summary = null, sensitive = false).copy(
                position = TimelinePosition(25_000, "r9"),
                reactionMember = "r9",
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

        val next = assertIs<ProfilePostsResult.Found>(service.morePosts(viewer, cursor)).page
        assertEquals(listOf("<p>保存済みの 1</p>"), next.posts.map { it.contentHtml })
        assertEquals(listOf("r1"), next.posts.map { it.postId })
        assertNull(next.nextCursor)
        assertTrue(repositories.timelineRepository.storeRequests.isEmpty())
    }

    @Test
    fun storedPostsOlderThanTheEndOfOutboxAreReadInPagesWithoutDuplicates() = runBlocking {
        outbox = { RemoteNotesPage(notes = listOf(remoteNote("<p>outbox</p>", "https://remote.example/notes/outbox", publishedAt = Instant.ofEpochSecond(100))), next = null) }
        // outbox に載らないブーストが 1 ページより多くある
        (1..25).reversed().forEach { index ->
            repositories.timelineRepository.actorPosts.add(
                storedPost("<p>ブースト $index</p>", summary = null, sensitive = false).copy(
                    position = TimelinePosition(index * 1_000L, "b$index"),
                    reactionMember = "r$index",
                    noteRef = TimelineNoteRef.Remote("https://other.example/notes/$index"),
                    boostedBy = TimelineBooster(actorUri = actorUri, username = "bob", displayName = "Bob", iconUrl = null, updatedAt = Instant.EPOCH, emojis = listOf()),
                ),
            )
        }
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "Bob", null, listOf(), Instant.EPOCH))
        following.setFollowing(viewer, actorUri, true)

        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(21, profile.posts.size)
        val next = assertIs<ProfilePostsResult.Found>(service.morePosts(viewer, assertNotNull(profile.postsNextCursor))).page
        val all = (profile.posts + next.posts).map { it.contentHtml }

        assertEquals(26, all.size)
        assertEquals(all.toSet().size, all.size)
        assertNull(next.nextCursor)
    }

    @Test
    fun oldPinnedPostIsNotRepeatedOnLaterPages() = runBlocking {
        featured = { listOf(remoteNote("<p>固定した古い投稿</p>", "https://remote.example/notes/old", publishedAt = Instant.ofEpochSecond(10))) }
        outbox = { from ->
            if (from == null) {
                RemoteNotesPage(
                    notes = listOf(remoteNote("<p>新しい投稿</p>", "https://remote.example/notes/new", publishedAt = Instant.ofEpochSecond(30))),
                    next = OutboxPosition(pageId = "page-2", itemOffset = 0),
                )
            } else {
                RemoteNotesPage(notes = listOf(remoteNote("<p>固定した古い投稿</p>", "https://remote.example/notes/old", publishedAt = Instant.ofEpochSecond(10))), next = null)
            }
        }

        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf("<p>固定した古い投稿</p>", "<p>新しい投稿</p>"), profile.posts.map { it.contentHtml })
        val cursor = assertNotNull(profile.postsNextCursor)
        val next = assertIs<ProfilePostsResult.Found>(service.morePosts(viewer, assertNotNull(ProfilePostCursor.decode(cursor.encode())))).page

        assertEquals(listOf(), next.posts)
    }

    @Test
    fun storedPostAtTheSameTimeAsPageBoundaryIsShownOnce() = runBlocking {
        outbox = { from ->
            if (from == null) {
                RemoteNotesPage(
                    notes = listOf(remoteNote("<p>境目</p>", "https://remote.example/notes/boundary", publishedAt = Instant.ofEpochSecond(20))),
                    next = OutboxPosition(pageId = "page-2", itemOffset = 0),
                )
            } else {
                RemoteNotesPage(notes = listOf(remoteNote("<p>同じ時刻</p>", "https://remote.example/notes/same-time", publishedAt = Instant.ofEpochSecond(20))), next = null)
            }
        }
        repositories.timelineRepository.actorPosts.add(
            storedPost("<p>同じ時刻</p>", summary = null, sensitive = false).copy(
                position = TimelinePosition(20_000, "r5"),
                reactionMember = "r5",
                noteRef = TimelineNoteRef.Remote("https://remote.example/notes/same-time"),
            ),
        )
        repositories.timelineRepository.actorPosts.add(
            storedPost("<p>境目</p>", summary = null, sensitive = false).copy(
                position = TimelinePosition(20_000, "r4"),
                reactionMember = "r4",
                noteRef = TimelineNoteRef.Remote("https://remote.example/notes/boundary"),
            ),
        )
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "Bob", null, listOf(), Instant.EPOCH))
        following.setFollowing(viewer, actorUri, true)

        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        val next = assertIs<ProfilePostsResult.Found>(service.morePosts(viewer, assertNotNull(profile.postsNextCursor))).page
        val all = (profile.posts + next.posts).map { it.contentHtml }

        assertEquals(listOf("<p>境目</p>", "<p>同じ時刻</p>"), all)
    }

    @Test
    fun remotePostsContinuationNeedsLoginAndReportsUnreadableOutbox() = runBlocking {
        val cursor = ProfilePostCursor.Remote(
            actorUri = actorUri,
            outbox = OutboxPosition(pageId = "page-2", itemOffset = 0),
            storedBefore = null,
            pinnedObjectUris = listOf(),
            shownAtBoundary = listOf(),
        )
        assertEquals(ProfilePostsResult.NotLoggedIn, service.morePosts(null, cursor))

        outbox = { null }
        assertEquals(ProfilePostsResult.Unreachable, service.morePosts(viewer, cursor))
    }

    @Test
    fun localProfileContinuesNotesAndBoostsWithoutGapsOrPinnedDuplicates() = runBlocking {
        (1..25).forEach { index ->
            repositories.noteRepository.notes.add(Note(PublicNoteId("note-$index"), "alice", "<p>投稿 $index</p>", Instant.ofEpochSecond(index * 10L), listOf()))
        }
        repositories.notePinRepository.pin(viewer, PublicNoteId("note-3"), Instant.EPOCH, NotePinService.MAX_PINS)
        repositories.timelineRepository.accountBoosts.add(
            storedPost("<p>ブースト</p>", summary = null, sensitive = false).copy(
                position = TimelinePosition(55_000, "a1"),
                boostedBy = TimelineBooster(actorUri = null, username = "alice", displayName = null, iconUrl = null, updatedAt = null, emojis = listOf()),
            ),
        )

        val first = assertNotNull(service.profile("alice", viewer))
        val next = assertIs<ProfilePostsResult.Found>(service.morePosts(viewer, assertNotNull(first.postsNextCursor))).page
        val all = (first.posts + next.posts).map { it.contentHtml }

        assertEquals("<p>投稿 3</p>", all.first())
        assertEquals(26, all.size)
        assertEquals(all.toSet().size, all.size)
        assertTrue("<p>ブースト</p>" in all)
        assertNull(next.nextCursor)
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
    fun localProfileShowsOwnBoostsAtTheTimeTheyWereBoosted() = runBlocking {
        repositories.noteRepository.notes.add(Note(PublicNoteId("note-alice"), "alice", "<p>自分の投稿</p>", Instant.ofEpochMilli(1_000), listOf()))
        repositories.timelineRepository.accountBoosts.add(
            storedPost("<p>ブーストした投稿</p>", summary = null, sensitive = false).copy(
                position = TimelinePosition(2_000, "a1"),
                boostedBy = TimelineBooster(actorUri = null, username = "alice", displayName = null, iconUrl = null, updatedAt = null, emojis = listOf()),
            ),
        )
        repositories.timelineRepository.accountBoosts.add(
            storedPost("<p>古いブースト</p>", summary = null, sensitive = false).copy(
                position = TimelinePosition(500, "a2"),
                actorUri = null,
                username = "carol",
                reactionMember = "l9",
                noteRef = TimelineNoteRef.Local(PublicNoteId("note-carol")),
                boostedBy = TimelineBooster(actorUri = null, username = "alice", displayName = null, iconUrl = null, updatedAt = null, emojis = listOf()),
            ),
        )

        val ownPosts = assertNotNull(service.profile("alice", viewer)).posts
        assertEquals(listOf("<p>ブーストした投稿</p>", "<p>自分の投稿</p>", "<p>古いブースト</p>"), ownPosts.map { it.contentHtml })
        assertEquals(listOf("a1", null, "a2"), ownPosts.map { it.postId })
        assertEquals(listOf("remote.example", null, "local.example"), ownPosts.map { it.boostedNoteAuthor?.host })
        assertEquals(listOf("r1", "l9"), listOf(ownPosts[0].reactionPostId, ownPosts[2].reactionPostId))
        assertEquals(listOf(null, null), listOf(ownPosts[0].deletableNoteId, ownPosts[2].deletableNoteId))

        val anonymousPosts = assertNotNull(service.profile("alice", null)).posts
        assertEquals(listOf(null, null, null), anonymousPosts.map { it.reactionPostId })
        assertNull(anonymousPosts.first().postId)
        assertNull(anonymousPosts.first().like)
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
                remoteNote("<p>固定した投稿</p>", "https://remote.example/notes/pinned"),
                remoteNote("<p>最新の投稿</p>", "https://remote.example/notes/1"),
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
        featured = { listOf(remoteNote("<p>相手のサーバーの本文</p>", "https://remote.example/notes/1")) }

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
            listOf(remoteNote("<p>固定した投稿</p>", "https://remote.example/notes/pinned"))
        }

        val posts = assertNotNull(service.profile("bob@remote.example", viewer)).posts

        assertEquals(listOf("<p>最新の投稿</p>"), posts.map { it.contentHtml })
    }

    private fun remoteNote(contentHtml: String, noteUri: String): RemoteNoteInput = remoteNote(contentHtml, noteUri, Instant.EPOCH)

    private fun remoteNote(contentHtml: String, noteUri: String, publishedAt: Instant): RemoteNoteInput =
        RemoteNoteInput(
            objectUri = noteUri,
            actorUri = actorUri,
            contentHtml = contentHtml,
            visibility = "public",
            localRecipients = setOf(),
            inReplyTo = null,
            summary = null,
            sensitive = false,
            publishedAt = publishedAt,
            receivedAt = publishedAt,
            images = listOf(),
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
            boostedByMe = null,
            reactionMember = "r1",
            noteRef = TimelineNoteRef.Remote("https://remote.example/notes/1"),
            boostCount = null,
            boostedBy = null,
            actorEmojis = listOf(),
            emojis = listOf(),
            deleted = false,
            boostUndone = false,
        )

    private companion object {
        val image = TimelinePostImage.Remote(url = "https://remote.example/a.png", description = null)
    }
}
