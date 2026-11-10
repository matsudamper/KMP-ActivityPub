package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.TimelineBooster
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

class FollowingGroupServiceTest {
    private val repositories = FakeRepositories()
    private val followingService = FollowingService(
        repositories.accountRepository,
        repositories.followingRepository,
        NoOpFollowDelivery,
        TimelineInvalidator {},
        "local.example",
        Clock.systemUTC(),
    )
    private val service = FollowingGroupService(
        accounts = repositories.accountRepository,
        groups = repositories.followingGroupRepository,
        following = repositories.followingRepository,
        followingService = followingService,
        clock = Clock.systemUTC(),
    )
    private val actorUri = "https://remote.example/users/bob"

    @Test
    fun createGroupRejectsBlankOrLongNamesAndMissingAccount() {
        val alice = repositories.accountRepository.add("alice", "hash", "type")
        assertEquals(CreateFollowingGroupResult.NotLoggedIn, service.createGroup(null, "ニュース"))
        assertEquals(CreateFollowingGroupResult.InvalidName, service.createGroup(alice, "  "))
        assertEquals(CreateFollowingGroupResult.InvalidName, service.createGroup(alice, "あ".repeat(31)))
        val created = assertIs<CreateFollowingGroupResult.Created>(service.createGroup(alice, " ニュース "))
        assertEquals("ニュース", created.group.name)
        assertEquals(listOf(created.group), service.groups(alice))
    }

    @Test
    fun followingIntoGroupAndMovingBackToHome() = runBlocking {
        val alice = repositories.accountRepository.add("alice", "hash", "type")
        val news = assertIs<CreateFollowingGroupResult.Created>(service.createGroup(alice, "ニュース")).group

        assertTrue(service.setFollowing(alice, actorUri, true, news.id))
        assertTrue(followingService.isFollowing(alice, actorUri))
        assertEquals(news.id, service.groupIdOf(alice, actorUri))

        assertTrue(service.setFollowing(alice, actorUri, true, null))
        assertNull(service.groupIdOf(alice, actorUri))

        assertTrue(service.setFollowing(alice, actorUri, false, null))
        assertFalse(followingService.isFollowing(alice, actorUri))
    }

    @Test
    fun otherAccountsGroupIsRejectedWithoutFollowing() = runBlocking {
        val alice = repositories.accountRepository.add("alice", "hash", "type")
        val carol = repositories.accountRepository.add("carol", "hash", "type")
        val carolsGroup = assertIs<CreateFollowingGroupResult.Created>(service.createGroup(carol, "ニュース")).group

        assertFalse(service.setFollowing(alice, actorUri, true, carolsGroup.id))
        assertFalse(service.setFollowing(alice, actorUri, true, FollowingGroupId(999)))
        assertFalse(followingService.isFollowing(alice, actorUri))
    }

    @Test
    fun postGoesToBoosterGroupOrAuthorGroupOrHome() = runBlocking {
        val alice = repositories.accountRepository.add("alice", "hash", "type")
        val news = assertIs<CreateFollowingGroupResult.Created>(service.createGroup(alice, "ニュース")).group
        val homeActorUri = "https://remote.example/users/dave"
        assertTrue(service.setFollowing(alice, actorUri, true, news.id))
        assertTrue(service.setFollowing(alice, homeActorUri, true, null))

        assertEquals(news.id, service.groupIdOfPost(alice, timelinePost(actorUri = actorUri, boosterActorUri = null)))
        assertEquals(news.id, service.groupIdOfPost(alice, timelinePost(actorUri = homeActorUri, boosterActorUri = actorUri)))
        assertNull(service.groupIdOfPost(alice, timelinePost(actorUri = actorUri, boosterActorUri = homeActorUri)))
        assertNull(service.groupIdOfPost(alice, timelinePost(actorUri = null, boosterActorUri = null)))
    }

    private fun timelinePost(actorUri: String?, boosterActorUri: String?) = TimelinePost(
        position = TimelinePosition(0, "1"),
        contentHtml = "本文",
        publishedAt = Instant.EPOCH,
        actorUri = actorUri,
        username = "bob",
        displayName = null,
        summary = null,
        sensitive = false,
        actorIconUrl = null,
        actorUpdatedAt = null,
        images = listOf(),
        emojis = listOf(),
        actorEmojis = listOf(),
        favouriteCount = null,
        stamps = listOf(),
        likedByMe = false,
        boostedByMe = null,
        noteRef = TimelineNoteRef.Remote("https://remote.example/notes/1"),
        boostCount = null,
        boostedBy = boosterActorUri?.let { TimelineBooster(it, "booster", null, null, listOf(), Instant.EPOCH) },
    )
}
