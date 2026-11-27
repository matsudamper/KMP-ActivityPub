package net.matsudamper.kmp.activitypub.logic

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.RemoteActorSearchRecord

class ProfileFollowCountsServiceTest {
    private val repositories = FakeRepositories()
    private val viewer = repositories.accountRepository.add("alice", "hash", "type")
    private val actorUri = "https://remote.example/users/bob"
    private val requests = mutableListOf<String>()
    private val remoteProfiles = object : RemoteUserProfiles {
        override suspend fun find(username: String, host: String): RemoteUserProfile? {
            requests.add("profile:$username@$host")
            return if (username == "bob") RemoteUserProfile(RemoteUser(actorUri, "bob", "Bob", null, listOf()), null, null, listOf()) else null
        }

        override suspend fun findByActorUri(actorUri: String): RemoteUserProfile = error("アクターの URL からは引かない")

        override suspend fun recentNotesPage(actorUri: String, from: OutboxPosition?): RemoteNotesPage = error("投稿は読まない")

        override suspend fun postSources(actorUri: String): RemoteProfilePostSources = error("投稿は読まない")

        override suspend fun followCounts(actorUri: String): ProfileFollowCounts {
            requests.add("counts:$actorUri")
            return ProfileFollowCounts(following = 3, followers = null)
        }
    }
    private val service = ProfileFollowCountsService(
        accounts = repositories.accountRepository,
        following = repositories.followingRepository,
        followers = repositories.followerRepository,
        remoteActors = repositories.remoteActorRepository,
        remoteProfiles = remoteProfiles,
        localDomain = "local.example",
    )

    @Test
    fun localUserCountsFromThisServer() = runBlocking {
        repositories.followingRepository.add(viewer, actorUri, Instant.EPOCH)

        assertEquals(ProfileFollowCounts(following = 1, followers = 0), service.counts("alice", viewer = null))
        assertEquals(listOf(), requests)
    }

    @Test
    fun remoteUserCountsFromRemoteServer() = runBlocking {
        assertEquals(ProfileFollowCounts(following = 3, followers = null), service.counts("bob@remote.example", viewer))
        assertEquals(listOf("profile:bob@remote.example", "counts:$actorUri"), requests)
    }

    @Test
    fun storedRemoteUserIsNotLookedUpAgain() = runBlocking {
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "Bob", null, listOf(), Instant.EPOCH))

        assertEquals(ProfileFollowCounts(following = 3, followers = null), service.counts("bob@remote.example", viewer))
        assertEquals(listOf("counts:$actorUri"), requests)
    }

    @Test
    fun remoteUserHasNoCountsWithoutLogin() = runBlocking {
        repositories.remoteActorRepository.add(RemoteActorSearchRecord(actorUri, "bob", "Bob", null, listOf(), Instant.EPOCH))

        assertEquals(ProfileFollowCounts(following = null, followers = null), service.counts("bob@remote.example", viewer = null))
        assertNull(service.counts("carol@remote.example", viewer = null))
        assertEquals(listOf(), requests)
    }

    @Test
    fun unknownUserIsNull() = runBlocking {
        assertNull(service.counts("carol", viewer))
        assertNull(service.counts("carol@remote.example", viewer))
    }
}
