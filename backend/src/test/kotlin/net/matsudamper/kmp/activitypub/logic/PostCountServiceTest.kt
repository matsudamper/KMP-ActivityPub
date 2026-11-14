package net.matsudamper.kmp.activitypub.logic

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.FakeNoteCountRepository
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

class PostCountServiceTest {
    @Test
    fun localPostCountsRepliesToItsNoteUrl() {
        val repository = FakeNoteCountRepository().apply { knownReplies["https://$DOMAIN/notes/local"] = 2 }
        val ref = TimelineNoteRef.Local(PublicNoteId("local"))

        val counts = service(repository, remote = { error("このサーバーの投稿は取りに行かない") }).countsOf(listOf(ref))

        assertEquals(mapOf<TimelineNoteRef, PostCounts>(ref to PostCounts(replyCount = 2, reportedFavouriteCount = null, reportedBoostCount = null)), counts)
    }

    @Test
    fun remotePostTakesLargerReplyCountAndReportedReactions() = runBlocking {
        val repository = FakeNoteCountRepository().apply {
            storedRemoteNotes += REMOTE
            knownReplies[REMOTE] = 3
        }
        val ref = TimelineNoteRef.Remote(REMOTE)
        val service = service(repository, remote = { FetchedNoteCounts(replyCount = 1, favouriteCount = 10, boostCount = null) })

        assertEquals(PostCounts(replyCount = 3, reportedFavouriteCount = 10, reportedBoostCount = null), service.refreshedCountsOf(ref))
    }

    @Test
    fun remotePostIsNotFetchedAgainWithinInterval() = runBlocking {
        val repository = FakeNoteCountRepository().apply { storedRemoteNotes += REMOTE }
        val ref = TimelineNoteRef.Remote(REMOTE)
        var fetchCount = 0
        val service = service(repository, remote = {
            fetchCount++
            FetchedNoteCounts(replyCount = 5, favouriteCount = null, boostCount = null)
        })

        service.refreshedCountsOf(ref)
        val counts = service.refreshedCountsOf(ref)

        assertEquals(1, fetchCount)
        assertEquals(5, counts.replyCount)
    }

    @Test
    fun failedFetchKeepsKnownCount() = runBlocking {
        val repository = FakeNoteCountRepository().apply {
            storedRemoteNotes += REMOTE
            knownReplies[REMOTE] = 1
        }

        val counts = service(repository, remote = { null }).refreshedCountsOf(TimelineNoteRef.Remote(REMOTE))

        assertEquals(PostCounts(replyCount = 1, reportedFavouriteCount = null, reportedBoostCount = null), counts)
    }

    private fun service(repository: FakeNoteCountRepository, remote: RemoteNoteCounts): PostCountService = PostCountService(
        domain = DOMAIN,
        counts = repository,
        remoteCounts = remote,
        clock = Clock.fixed(NOW, ZoneOffset.UTC),
    )

    private companion object {
        const val DOMAIN = "local.example"
        const val REMOTE = "https://remote.example/notes/1"
        val NOW: Instant = Instant.parse("2026-11-01T12:00:00Z")
    }
}
