package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kotpub.actor.RemoteActor
import net.matsudamper.kotpub.quote.QuoteApprovals

/**
 * @param onEnqueued Accept を積んだ後に呼ぶ。相手の画面では引用が保留のまま待っているので、次の巡回を待たずに送らせる
 */
internal class QueuedQuoteApprovals(
    private val accountRepository: AccountRepository,
    private val deliveryQueueRepository: DeliveryQueueRepository,
    private val blocks: InboxBlocks,
    private val clock: Clock,
    private val onEnqueued: () -> Unit,
) : QuoteApprovals {
    override fun enqueueAccept(
        username: String,
        quoter: RemoteActor,
        acceptBody: String,
    ): Boolean {
        if (blocks.blocks(username, quoter.actorId)) return false
        val account = accountRepository.findByUsername(username) ?: return false
        deliveryQueueRepository.enqueueQuoteAccept(
            accountId = account.id,
            inbox = quoter.inbox,
            body = acceptBody,
            now = clock.instant(),
        )
        onEnqueued()
        return true
    }
}
