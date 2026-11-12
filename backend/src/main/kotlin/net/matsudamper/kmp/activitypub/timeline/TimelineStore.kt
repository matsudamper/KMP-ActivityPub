package net.matsudamper.kmp.activitypub.timeline

import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId

interface TimelineStore : AutoCloseable {
    fun ready(feed: TimelineFeed): Boolean
    fun lock(feed: TimelineFeed): Boolean
    fun markBuilt(feed: TimelineFeed)
    fun unlock(feed: TimelineFeed)
    fun invalidate(feed: TimelineFeed)
    fun expire(feed: TimelineFeed)
    fun distribute(feeds: List<TimelineFeed>, position: TimelinePosition): Int
    fun appendRebuilt(feed: TimelineFeed, positions: List<TimelinePosition>)
    fun read(feed: TimelineFeed, after: TimelinePosition?, limit: Int): List<TimelinePosition>
    fun enqueue(position: TimelinePosition)
    fun initializeGroup()
    fun jobs(consumer: String, block: Boolean): List<FanoutJob>
    fun acknowledge(id: String)
}

/**
 * アカウントの購読先 1 つ分のタイムライン
 *
 * @param groupId null ならメイン
 */
data class TimelineFeed(val accountId: AccountId, val groupId: FollowingGroupId?)

data class FanoutJob(val id: String, val position: TimelinePosition)
