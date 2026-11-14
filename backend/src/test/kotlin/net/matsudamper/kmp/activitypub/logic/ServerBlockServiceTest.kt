package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.TimelineBooster
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.timeline.AccountEvent
import net.matsudamper.kmp.activitypub.timeline.FakeTimelineRepository

class ServerBlockServiceTest {
    private val repositories = FakeRepositories()
    private val service = ServerBlockService(
        accounts = repositories.accountRepository,
        serverBlocks = repositories.serverBlockRepository,
        clock = Clock.systemUTC(),
    )
    private val alice = repositories.accountRepository.add("alice", "hash", "type")
    private val bob = repositories.accountRepository.add("bob", "hash", "type")

    @Test
    fun hostIsTakenFromUrlOrAccountName() {
        listOf(
            " Remote.Example " to "remote.example",
            "https://remote.example/@carol" to "remote.example",
            "@carol@remote.example" to "remote.example",
            "carol@remote.example" to "remote.example",
            "http://remote.example:8080/users/carol" to "remote.example:8080",
            "例え.テスト" to "xn--r8jz45g.xn--zckzah",
        ).forEach { (input, host) ->
            assertEquals(BlockServerResult.Blocked(host), service.block(alice, input), input)
        }
        assertEquals(listOf("xn--r8jz45g.xn--zckzah", "remote.example:8080", "remote.example"), service.hosts(alice))
        assertEquals(listOf(), service.hosts(bob))
    }

    @Test
    fun invalidServerOrNoAccountIsRejected() {
        listOf("", "  ", "https://", "remote example", "@carol@").forEach { input ->
            assertEquals(BlockServerResult.InvalidServer, service.block(alice, input), input)
        }
        assertEquals(BlockServerResult.NotLoggedIn, service.block(null, "remote.example"))
        assertEquals(BlockServerResult.NotLoggedIn, service.block(AccountId(99), "remote.example"))
        assertEquals(UnblockServerResult.NotLoggedIn, service.unblock(null, "remote.example"))
        assertEquals(listOf(), service.hosts(alice))
    }

    @Test
    fun unblockRemovesOnlyThatHost() {
        service.block(alice, "remote.example")
        service.block(alice, "other.example")
        assertEquals(UnblockServerResult.Unblocked, service.unblock(alice, "remote.example"))
        assertEquals(UnblockServerResult.Unblocked, service.unblock(alice, "remote.example"))
        assertEquals(listOf("other.example"), service.hosts(alice))
    }

    @Test
    fun postsFromBlockedServerAuthorOrBoosterAreHidden() {
        service.block(alice, "remote.example")
        val timeline = FakeTimelineRepository()
        timeline.positions.add(TimelinePosition(1, "r1"))
        val post = timeline.hydrate(alice, listOf("r1")).single()
        val blockedAuthor = post.copy(actorUri = "https://REMOTE.example/users/carol")
        val blockedBooster = post.copy(
            actorUri = "https://other.example/users/dave",
            boostedBy = TimelineBooster(actorUri = "https://remote.example/users/carol", username = "carol", displayName = null, iconUrl = null, updatedAt = Instant.EPOCH, emojis = listOf()),
        )
        val otherServer = post.copy(actorUri = "https://other.example/users/dave")
        val subdomain = post.copy(actorUri = "https://sub.remote.example/users/erin")

        assertTrue(service.hides(alice, AccountEvent.PostArrived(blockedAuthor)))
        assertTrue(service.hides(alice, AccountEvent.PostArrived(blockedBooster)))
        assertFalse(service.hides(alice, AccountEvent.PostArrived(otherServer)))
        assertFalse(service.hides(alice, AccountEvent.PostArrived(subdomain)))
        assertFalse(service.hides(alice, AccountEvent.PostArrived(post)))
        assertFalse(service.hides(bob, AccountEvent.PostArrived(blockedAuthor)))
    }
}
