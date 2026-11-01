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
            return RemoteUserProfile(RemoteUser(actorUri, username, "Bob", null), "自己紹介")
        }

        override suspend fun recentPosts(actorUri: String): List<ProfilePost> {
            requests.add("posts")
            return listOf(ProfilePost("<p>最新の投稿</p>", Instant.EPOCH.toString()))
        }

        override suspend fun findByActorUri(actorUri: String): RemoteUserProfile {
            requests.add("profile:$actorUri")
            return RemoteUserProfile(RemoteUser(actorUri, "bob", "Bob", null), "自己紹介")
        }
    }
    private val service = UserProfileService(repositories.accountRepository, repositories.remoteActorRepository, repositories.noteRepository, following, remoteProfiles, "local.example")

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
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "保存済みの Bob", null, Instant.EPOCH))
        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf("profile:$actorUri", "posts"), requests)
        assertEquals(actorUri, profile.user.actorUri)
        assertEquals("自己紹介", profile.biography)
        assertEquals("<p>最新の投稿</p>", profile.posts.single().contentHtml)
    }

    @Test
    fun followedUserUsesStoredProfileWithoutRemoteRequests() = runBlocking {
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "保存済みの Bob", null, Instant.EPOCH))
        following.setFollowing(viewer, actorUri, true)
        val profile = assertNotNull(service.profile("bob@remote.example", viewer))
        assertEquals(listOf(), requests)
        assertEquals("保存済みの Bob", profile.user.displayName)
        assertEquals(true, profile.following)
        assertEquals(listOf(), profile.posts)
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
        repositories.noteRepository.notes.add(Note(PublicNoteId("note-1"), "alice", "<p>保存済みの投稿</p>", Instant.EPOCH))
        val profile = assertNotNull(service.profile("alice", viewer))
        assertEquals(listOf(), requests)
        assertEquals("<p>保存済みの投稿</p>", profile.posts.single().contentHtml)
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
}
