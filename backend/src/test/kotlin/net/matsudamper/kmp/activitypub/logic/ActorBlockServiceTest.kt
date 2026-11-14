package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.BlockedActor
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.AccountEvent
import net.matsudamper.kmp.activitypub.timeline.FakeTimelineRepository

class ActorBlockServiceTest {
    private val repositories = FakeRepositories()
    private val alice = repositories.accountRepository.add("alice", "hash", "type")
    private val bob = repositories.accountRepository.add("bob", "hash", "type")
    private val carolUri = "https://remote.example/users/carol"
    private val remoteProfiles = object : RemoteUserProfiles {
        override suspend fun find(username: String, host: String): RemoteUserProfile? =
            if (username == "carol" && host == "remote.example") RemoteUserProfile(RemoteUser(carolUri, "carol", "Carol", null, listOf()), null, null) else null

        override suspend fun findByActorUri(actorUri: String): RemoteUserProfile? = null

        override suspend fun recentNotesPage(actorUri: String, from: OutboxPosition?): RemoteNotesPage? = error("投稿は読まない")

        override suspend fun postSources(actorUri: String): RemoteProfilePostSources = error("投稿は読まない")
    }
    private val service = ActorBlockService(
        accounts = repositories.accountRepository,
        actorBlocks = repositories.actorBlockRepository,
        profiles = UserProfileService(
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
            following = FollowingService(repositories.accountRepository, repositories.followingRepository, NoOpFollowDelivery, TimelineInvalidator {}, "local.example", Clock.systemUTC()),
            remoteProfiles = remoteProfiles,
            localDomain = "local.example",
        ),
        clock = Clock.systemUTC(),
    )
    private val blockLists = BlockLists(repositories.serverBlockRepository, repositories.actorBlockRepository)

    @Test
    fun remoteAccountIsBlockedByActorUriPerAccount() = runBlocking {
        val carol = BlockedActor(actorUri = carolUri, acct = "carol@remote.example")
        assertEquals(BlockActorResult.Blocked(carol), service.block(alice, "@carol@remote.example"))
        assertEquals(BlockActorResult.Blocked(carol), service.block(alice, "carol@remote.example"))
        assertEquals(listOf(carol), service.list(alice))
        assertEquals(listOf(), service.list(bob))
    }

    @Test
    fun localMissingOrNoAccountIsRejected() = runBlocking {
        assertEquals(BlockActorResult.LocalAccount, service.block(alice, "bob"))
        assertEquals(BlockActorResult.NotFound, service.block(alice, "dave@remote.example"))
        assertEquals(BlockActorResult.NotLoggedIn, service.block(null, "carol@remote.example"))
        assertEquals(BlockActorResult.NotLoggedIn, service.block(AccountId(99), "carol@remote.example"))
        assertEquals(UnblockActorResult.NotLoggedIn, service.unblock(null, carolUri))
        assertEquals(listOf(), service.list(alice))
    }

    @Test
    fun blockedActorPostsAndBoostsAreHiddenUntilUnblocked() = runBlocking {
        service.block(alice, "carol@remote.example")
        val timeline = FakeTimelineRepository()
        timeline.positions.add(TimelinePosition(1, "r1"))
        val post = timeline.hydrate(alice, listOf("r1")).single()
        val byCarol = post.copy(actorUri = carolUri)
        val byOtherOnSameServer = post.copy(actorUri = "https://remote.example/users/dave")

        assertTrue(blockLists.hides(alice, AccountEvent.PostArrived(byCarol)))
        assertFalse(blockLists.hides(alice, AccountEvent.PostArrived(byOtherOnSameServer)))
        assertFalse(blockLists.hides(bob, AccountEvent.PostArrived(byCarol)))

        assertEquals(UnblockActorResult.Unblocked, service.unblock(alice, carolUri))
        assertFalse(blockLists.hides(alice, AccountEvent.PostArrived(byCarol)))
    }
}
