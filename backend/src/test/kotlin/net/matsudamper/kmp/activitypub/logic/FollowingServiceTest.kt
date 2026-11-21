package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

class FollowingServiceTest {
    @Test
    fun onlyChangesByExistingAccountsReachDelivery() = runBlocking {
        val repositories = FakeRepositories()
        val accountId = repositories.accountRepository.add("alice", "hash", "hash-type")
        val deliveries = mutableListOf<Boolean>()
        val service = FollowingService(
            accounts = repositories.accountRepository,
            following = repositories.followingRepository,
            blockLists = BlockLists(repositories.serverBlockRepository, repositories.actorBlockRepository),
            delivery = object : FollowDelivery {
                override suspend fun send(accountId: AccountId, actorUri: String, followed: Boolean, followId: String?): FollowDeliveryResult {
                    deliveries.add(followed)
                    return FollowDeliveryResult.Proceeded
                }
            },
            invalidator = TimelineInvalidator {},
            localDomain = "local.example",
            clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        )
        val actorUri = "https://remote.example/users/bob"
        assertFalse(service.setFollowing(null, actorUri, true))
        assertFalse(service.setFollowing(AccountId(999), actorUri, true))
        listOf("invalid", "file:///tmp/actor", "https://LOCAL.example/users/bob").forEach {
            assertFalse(service.setFollowing(accountId, it, true))
        }
        assertFalse(service.isFollowing(accountId, actorUri))
        assertTrue(service.setFollowing(accountId, actorUri, true))
        assertTrue(service.setFollowing(accountId, actorUri, true))
        assertTrue(service.isFollowing(accountId, actorUri))
        assertFalse(service.isFollowing(null, actorUri))
        assertFalse(service.isFollowing(accountId, null))
        assertTrue(service.setFollowing(accountId, actorUri, false))
        assertTrue(service.setFollowing(accountId, actorUri, false))
        assertFalse(service.isFollowing(accountId, actorUri))
        assertEquals(listOf(true, false), deliveries)
        assertTrue(service.setFollowing(accountId, actorUri, true))
        assertEquals(listOf(true, false, true), deliveries)
    }

    @Test
    fun deliveryFailureDoesNotRestoreLocalFollowing() = runBlocking {
        val repositories = FakeRepositories()
        val account = repositories.accountRepository.add("alice", "hash", "type")
        val actor = "https://remote.example/users/bob"
        repositories.followingRepository.add(account, actor, Instant.EPOCH)
        val invalidated = mutableListOf<AccountId>()
        val service = FollowingService(
            repositories.accountRepository,
            repositories.followingRepository,
            BlockLists(repositories.serverBlockRepository, repositories.actorBlockRepository),
            object : FollowDelivery {
                override suspend fun send(accountId: AccountId, actorUri: String, followed: Boolean, followId: String?): FollowDeliveryResult {
                    error("配信先を取得できない")
                }
            },
            TimelineInvalidator { invalidated.add(it) },
            "local.example",
            Clock.systemUTC(),
        )
        assertTrue(service.setFollowing(account, actor, false))
        assertFalse(service.isFollowing(account, actor))
        assertEquals(listOf(account), invalidated)
    }

    @Test
    fun moveFollowingFollowsMovedToAndUnfollowsMovedFromKeepingGroup() {
        runBlocking {
            val repositories = FakeRepositories()
            val alice = repositories.accountRepository.add("alice", "hash", "type")
            val bob = repositories.accountRepository.add("bob", "hash", "type")
            val carol = repositories.accountRepository.add("carol", "hash", "type")
            val movedFrom = "https://old.example/users/dave"
            val movedTo = "https://new.example/users/dave"
            val group = FollowingGroupId(1L)
            repositories.followingRepository.add(alice, movedFrom, Instant.EPOCH)
            repositories.followingRepository.setGroup(alice, movedFrom, group)
            repositories.followingRepository.add(bob, movedFrom, Instant.EPOCH)
            val deliveries = mutableListOf<Triple<AccountId, String, Boolean>>()
            val service = FollowingService(
                accounts = repositories.accountRepository,
                following = repositories.followingRepository,
                blockLists = BlockLists(repositories.serverBlockRepository, repositories.actorBlockRepository),
                delivery = object : FollowDelivery {
                    override suspend fun send(accountId: AccountId, actorUri: String, followed: Boolean, followId: String?): FollowDeliveryResult {
                        deliveries.add(Triple(accountId, actorUri, followed))
                        return FollowDeliveryResult.Proceeded
                    }
                },
                invalidator = TimelineInvalidator {},
                localDomain = "local.example",
                clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            )

            service.moveFollowing(movedFromActorUri = movedFrom, movedToActorUri = movedTo)

            listOf(alice, bob).forEach { accountId ->
                assertTrue(service.isFollowing(accountId, movedTo))
                assertFalse(service.isFollowing(accountId, movedFrom))
            }
            assertFalse(service.isFollowing(carol, movedTo))
            assertEquals(group, repositories.followingRepository.groupIdOf(alice, movedTo))
            assertNull(repositories.followingRepository.groupIdOf(bob, movedTo))
            assertEquals(
                listOf(
                    Triple(alice, movedTo, true),
                    Triple(alice, movedFrom, false),
                    Triple(bob, movedTo, true),
                    Triple(bob, movedFrom, false),
                ),
                deliveries,
            )
        }
    }
}
