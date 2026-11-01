package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.shared.AccountId

class FollowingServiceTest {
    @Test
    fun onlyChangesByExistingAccountsReachDelivery() {
        val repositories = FakeRepositories()
        val accountId = repositories.accountRepository.add("alice", "hash", "hash-type")
        val deliveries = mutableListOf<Boolean>()
        val service = FollowingService(
            accounts = repositories.accountRepository,
            following = repositories.followingRepository,
            delivery = object : FollowDelivery {
                override fun send(accountId: AccountId, actorUri: String, followed: Boolean) {
                    deliveries.add(followed)
                }
            },
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
}
