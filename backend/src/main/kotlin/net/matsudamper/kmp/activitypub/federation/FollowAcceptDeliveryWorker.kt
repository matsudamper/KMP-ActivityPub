package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.matsudamper.kmp.activitypub.repository.FollowerRepository
import net.matsudamper.kmp.activitypub.repository.PendingFollowAccept
import net.matsudamper.kotpub.delivery.ActivityDelivery
import net.matsudamper.kotpub.delivery.DeliveryResult
import org.slf4j.LoggerFactory

/**
 * 積まれた Accept を相手の inbox に送る。送れたらフォローが成立する
 */
internal class FollowAcceptDeliveryWorker(
    private val followerRepository: FollowerRepository,
    private val localActors: LocalActors,
    private val delivery: ActivityDelivery,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(FollowAcceptDeliveryWorker::class.java)
    private val wakeUpSignal = Channel<Unit>(Channel.CONFLATED)

    fun wakeUp() {
        wakeUpSignal.trySend(Unit)
    }

    suspend fun run() {
        while (true) {
            val hasMore =
                try {
                    deliverDue()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("Accept の配信キューを処理できなかった", e)
                    false
                }
            if (!hasMore) {
                withTimeoutOrNull(POLL_INTERVAL) { wakeUpSignal.receive() }
            }
        }
    }

    /**
     * @return 期限の来たものがまだ残っていそうなら true
     */
    suspend fun deliverDue(): Boolean {
        val dueDeliveries = withContext(Dispatchers.IO) {
            followerRepository.findDueAcceptDeliveries(now = clock.instant(), limit = BATCH_SIZE)
        }
        dueDeliveries.forEach { pending ->
            try {
                deliverOne(pending)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 例外のまま抜けると、同じ行が次の巡回でも先頭に来て後ろの配信が止まり続ける
                logger.warn("Accept の配信を処理できなかった: ${pending.followeeUsername} → ${pending.inbox}", e)
                withContext(Dispatchers.IO) { recordFailure(pending, reason = e.toString(), retryable = true) }
            }
        }
        return dueDeliveries.size >= BATCH_SIZE
    }

    private suspend fun deliverOne(pending: PendingFollowAccept) {
        val sender = withContext(Dispatchers.IO) { localActors.find(pending.followeeUsername) }
        if (sender == null) {
            logger.warn("Accept の送り主の鍵が無いので諦める: ${pending.followeeUsername} → ${pending.inbox}")
            withContext(Dispatchers.IO) { followerRepository.abandonAcceptDelivery(pending) }
            return
        }

        val result = delivery.deliver(inbox = pending.inbox, sender = sender, body = pending.body.encodeToByteArray())
        withContext(Dispatchers.IO) {
            when (result) {
                DeliveryResult.Delivered -> {
                    followerRepository.completeAcceptDelivery(pending, acceptedAt = clock.instant())
                    logger.info("Accept を送ってフォローが成立した: ${pending.followeeUsername} → ${pending.inbox}")
                }

                is DeliveryResult.Failed -> {
                    recordFailure(pending, reason = result.reason, retryable = result.retryable)
                }
            }
        }
    }

    private fun recordFailure(
        pending: PendingFollowAccept,
        reason: String,
        retryable: Boolean,
    ) {
        val attempts = pending.attempts + 1
        if (!retryable || attempts >= MAX_ATTEMPTS) {
            followerRepository.abandonAcceptDelivery(pending)
            logger.warn("Accept を送れないので諦めた: ${pending.followeeUsername} 試行=$attempts 回 $reason")
        } else {
            val nextAttemptAt = clock.instant().plus(retryDelay(attempts).toJavaDuration())
            followerRepository.rescheduleAcceptDelivery(pending, nextAttemptAt = nextAttemptAt)
            logger.info("Accept を送れなかったので $nextAttemptAt に送り直す: ${pending.followeeUsername} $reason")
        }
    }

    private fun retryDelay(attempts: Int): Duration =
        (1.minutes * (1 shl (attempts - 1).coerceAtMost(MAX_BACKOFF_SHIFT))).coerceAtMost(MAX_RETRY_DELAY)

    private companion object {
        const val BATCH_SIZE = 20
        const val MAX_ATTEMPTS = 10
        const val MAX_BACKOFF_SHIFT = 10
        val MAX_RETRY_DELAY: Duration = 6.hours
        val POLL_INTERVAL: Duration = 30.seconds
    }
}
