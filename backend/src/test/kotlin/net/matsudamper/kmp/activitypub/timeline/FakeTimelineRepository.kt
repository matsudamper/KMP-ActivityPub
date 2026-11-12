package net.matsudamper.kmp.activitypub.timeline

import java.time.Instant
import net.matsudamper.kmp.activitypub.repository.RemoteBoostInput
import net.matsudamper.kmp.activitypub.repository.RemoteNoteInput
import net.matsudamper.kmp.activitypub.repository.RemoteNoteReceptionInterest
import net.matsudamper.kmp.activitypub.repository.TimelineNoteRef
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRecipient
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

internal class FakeTimelineRepository : TimelineRepository {
    val positions = mutableListOf<TimelinePosition>()
    val pageRequests = mutableListOf<TimelinePosition?>()
    val events = mutableListOf<String>()
    val received = mutableListOf<RemoteNoteInput>()
    val receivedBoosts = mutableListOf<RemoteBoostInput>()
    val actorPosts = mutableListOf<TimelinePost>()
    val accountBoosts = mutableListOf<TimelinePost>()
    val storedFetchedNotes = mutableMapOf<String, TimelinePost>()

    var interested = true
    override fun receptionInterest(actorUri: String, localRecipients: Set<String>): RemoteNoteReceptionInterest =
        RemoteNoteReceptionInterest(if (interested) 1 else 0, localRecipients.size.toLong())

    override fun receive(note: RemoteNoteInput): TimelinePosition? {
        received.add(note)
        return TimelinePosition(1, "r1")
    }
    override fun receiveBoost(boost: RemoteBoostInput): TimelinePosition? {
        receivedBoosts.add(boost)
        return TimelinePosition(1, "b1")
    }

    override fun deleteRemote(actorUri: String, objectUri: String, deletedAt: Instant): List<String> = listOf()
    override fun hasRemoteNote(objectUri: String): Boolean = received.any { it.objectUri == objectUri }
    override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<TimelineRecipient> = listOf()
    override fun touch(accountId: AccountId, now: Instant, updateIntervalSeconds: Long) = Unit
    override fun rebuild(accountId: AccountId, groupId: FollowingGroupId?, limit: Int): List<TimelinePosition> {
        events.add("rebuild")
        return sorted().take(limit)
    }

    override fun page(accountId: AccountId, groupId: FollowingGroupId?, after: TimelinePosition?, limit: Int): List<TimelinePosition> {
        pageRequests.add(after)
        return sorted().filter { after == null || it.score < after.score || (it.score == after.score && it.member < after.member) }.take(limit)
    }

    override fun hydrate(accountId: AccountId, members: List<String>): List<TimelinePost> = positions.filter { it.member in members }.map {
        TimelinePost(
            it, "本文", Instant.EPOCH, null, "alice", null, null, false, null, null, listOf(), listOf(), listOf(), null, listOf(), false, null, it.member,
            TimelineNoteRef.Local(
                PublicNoteId(it.member),
            ),
            null, null, false,
        )
    }

    override fun actorPosts(accountId: AccountId, actorUri: String, limit: Int): List<TimelinePost> =
        actorPosts.filter { (it.boostedBy?.actorUri ?: it.actorUri) == actorUri }.take(limit)

    override fun localPosition(publicId: String): TimelinePosition? = null

    override fun accountBoosts(booster: AccountId, viewer: AccountId?, limit: Int): List<TimelinePost> = accountBoosts.toList()

    override fun storeFetchedNotes(viewer: AccountId, notes: List<RemoteNoteInput>): Map<String, TimelinePost> =
        storedFetchedNotes.filterKeys { objectUri -> notes.any { it.objectUri == objectUri } }

    private fun sorted(): List<TimelinePosition> = positions.sortedWith(compareByDescending<TimelinePosition> { it.score }.thenByDescending { it.member })
}
