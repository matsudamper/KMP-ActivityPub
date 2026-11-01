package net.matsudamper.kmp.activitypub.timeline

import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.shared.AccountId

interface TimelineStore : AutoCloseable {
    fun built(accountId: AccountId): Boolean
    fun lock(accountId: AccountId): Boolean
    fun markBuilt(accountId: AccountId)
    fun unlock(accountId: AccountId)
    fun invalidate(accountId: AccountId)
    fun expire(accountId: AccountId)
    fun distribute(accountIds: List<AccountId>, position: TimelinePosition)
    fun appendRebuilt(accountId: AccountId, positions: List<TimelinePosition>)
    fun read(accountId: AccountId, after: TimelinePosition?, limit: Int): List<TimelinePosition>
    fun enqueue(position: TimelinePosition)
    fun initializeGroup()
    fun jobs(consumer: String, block: Boolean): List<FanoutJob>
    fun acknowledge(id: String)
}

data class FanoutJob(val id: String, val position: TimelinePosition)
