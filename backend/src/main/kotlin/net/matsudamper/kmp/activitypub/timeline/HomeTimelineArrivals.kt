package net.matsudamper.kmp.activitypub.timeline

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId

/**
 * サーバーは 1 台の前提なので、配布した投稿はプロセス内だけで購読者へ流す。
 * 購読者が詰まっても配布を止めないよう、溢れたら古いものから捨てる
 */
class HomeTimelineArrivals(
    private val repository: TimelineRepository,
) {
    private val arrivals = MutableSharedFlow<Arrival>(
        extraBufferCapacity = 1024,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    fun publish(recipients: List<AccountId>, position: TimelinePosition) {
        if (recipients.isEmpty()) return
        arrivals.tryEmit(Arrival(recipients.toSet(), position))
    }

    /**
     * 投稿を DB から読むので、blocking してよい dispatcher で集める
     */
    fun posts(accountId: AccountId): Flow<TimelinePost> =
        arrivals
            .filter { accountId in it.recipients }
            .mapNotNull { repository.hydrate(accountId, listOf(it.position.member)).singleOrNull() }

    private class Arrival(
        val recipients: Set<AccountId>,
        val position: TimelinePosition,
    )
}
