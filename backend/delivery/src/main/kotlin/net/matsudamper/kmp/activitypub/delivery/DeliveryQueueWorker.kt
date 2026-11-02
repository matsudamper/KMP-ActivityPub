package net.matsudamper.kmp.activitypub.delivery

import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import net.matsudamper.kmp.activitypub.repository.ClaimedDelivery
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.inOpenTelemetrySuspendSpan
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import net.matsudamper.kmp.activitypub.telemetry.withoutPollingSpans
import net.matsudamper.kotpub.delivery.ActivityDelivery
import net.matsudamper.kotpub.delivery.DeliveryResult
import org.slf4j.LoggerFactory

/**
 * 配信キューの行を相手の inbox に送る。Accept なら送れたときにフォローが成立する
 *
 * 起動時に delivering のまま残った行を戻すので、同じ DB に対してプロセスを 2 つ動かすと、
 * 動いている方が送っている行まで巻き戻して二重に送る
 */
class DeliveryQueueWorker(
    private val queue: DeliveryQueueRepository,
    private val senders: DeliverySenders,
    private val delivery: ActivityDelivery,
    private val retryPolicy: DeliveryRetryPolicy,
    private val clock: Clock,
    openTelemetry: OpenTelemetry = OpenTelemetryInitializer.get(),
) {
    private val tracer = openTelemetry.getTracer("activitypub-delivery")
    private val logger = LoggerFactory.getLogger(DeliveryQueueWorker::class.java)
    private val wakeUpSignal = Channel<Unit>(Channel.CONFLATED)

    fun wakeUp() {
        wakeUpSignal.trySend(Unit)
    }

    suspend fun run() {
        recoverDelivering()
        while (true) {
            val hasMore =
                try {
                    deliverDue()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.warn("配信キューを処理できなかった", e)
                    // 失敗を記録できずに抜けると、取り出した行が delivering のまま残る。
                    // 送っているのはこのループだけなので、ここで戻しても送信中の行を巻き戻さない
                    recoverDelivering()
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
        val claimed = claim()
        claimed.forEach { row ->
            inOpenTelemetrySuspendSpan(tracer, "DeliveryQueue.deliver", true) { span ->
                span.setAttribute(DeliverySpan.ID, row.id)
                span.setAttribute(DeliverySpan.KIND, row.kind)
                span.setAttribute(DeliverySpan.SENDER, row.username)
                span.setAttribute(DeliverySpan.INBOX, row.inbox)
                span.setAttribute(DeliverySpan.ATTEMPTS, row.attempts.toLong())
                try {
                    deliverOne(row)
                } catch (e: CancellationException) {
                    DeliverySpan.outcome("cancelled")
                    throw e
                } catch (e: Exception) {
                    // delivering のまま残すと、次の起動まで二度と取り出されない
                    logger.warn("配信を処理できなかった: ${row.username} → ${row.inbox}", e)
                    span.recordException(e)
                    withOpenTelemetryContext { recordFailure(row, reason = e.toString(), retryable = true) }
                }
            }
        }
        return claimed.size >= BATCH_SIZE
    }

    private suspend fun claim(): List<ClaimedDelivery> {
        val startedAt = clock.instant()
        val claimed = try {
            withoutPollingSpans {
                withOpenTelemetryContext { queue.claim(now = startedAt, limit = BATCH_SIZE) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            recordClaimSpan(startedAt, null, e)
            throw e
        }
        if (claimed.isNotEmpty()) recordClaimSpan(startedAt, claimed.size, null)
        return claimed
    }

    private fun recordClaimSpan(startedAt: Instant, count: Int?, failure: Exception?) {
        val span = tracer.spanBuilder("DeliveryQueue.claim").setNoParent().setStartTimestamp(startedAt).startSpan()
        if (count != null) span.setAttribute(DeliverySpan.CLAIMED_COUNT, count.toLong())
        if (failure == null) {
            span.setAttribute(DeliverySpan.OUTCOME, "claimed")
        } else {
            span.setAttribute(DeliverySpan.OUTCOME, "failed")
            span.setAttribute(DeliverySpan.FAILURE_REASON, failure.toString())
            span.recordException(failure)
            span.setStatus(StatusCode.ERROR)
        }
        span.end()
    }

    /**
     * 戻せないまま続けると、delivering の行が次の起動まで送られない
     */
    private suspend fun recoverDelivering() {
        while (true) {
            try {
                val count = inOpenTelemetrySuspendSpan(tracer, "DeliveryQueue.recoverDelivering", true) { span ->
                    val recovered = withOpenTelemetryContext { queue.recoverDelivering(now = clock.instant()) }
                    span.setAttribute(DeliverySpan.RECOVERED_COUNT, recovered.toLong())
                    span.setAttribute(DeliverySpan.OUTCOME, "recovered")
                    recovered
                }
                if (count > 0) logger.info("送っている途中で止まっていた配信 $count 件を送り直す")
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("送っている途中で止まっていた配信を戻せなかった", e)
                delay(POLL_INTERVAL)
            }
        }
    }

    private suspend fun deliverOne(row: ClaimedDelivery) {
        val sender = withOpenTelemetryContext { senders.find(row.username) }
        if (sender == null) {
            logger.warn("配信の送り主の鍵が無いので諦める: ${row.username} → ${row.inbox}")
            DeliverySpan.failed("送り主の鍵が無い")
            DeliverySpan.outcome("gave_up.no_account")
            withOpenTelemetryContext { queue.giveUp(row.id, error = "送り主の鍵が無い") }
            return
        }

        val result = delivery.deliver(inbox = row.inbox, sender = sender, body = row.body.encodeToByteArray())
        withOpenTelemetryContext {
            when (result) {
                DeliveryResult.Delivered -> {
                    queue.markDelivered(row.id, deliveredAt = clock.instant())
                    DeliverySpan.outcome("delivered")
                    logger.info("配信した: ${row.username} → ${row.inbox}")
                }

                is DeliveryResult.Failed -> {
                    recordFailure(row, reason = result.reason, retryable = result.retryable)
                }
            }
        }
    }

    private fun recordFailure(
        row: ClaimedDelivery,
        reason: String,
        retryable: Boolean,
    ) {
        DeliverySpan.failed(reason)
        val nextAttemptAt = retryPolicy.nextAttemptAt(attempts = row.attempts, enqueuedAt = row.enqueuedAt, now = clock.instant())
        if (!retryable || nextAttemptAt == null) {
            DeliverySpan.outcome(if (retryable) "gave_up.retry_exhausted" else "gave_up.rejected")
            queue.giveUp(row.id, error = reason)
            logger.warn("配信を諦めた: ${row.username} → ${row.inbox} 試行=${row.attempts} 回 $reason")
        } else {
            DeliverySpan.outcome("retry_scheduled")
            queue.scheduleRetry(row.id, nextAttemptAt = nextAttemptAt, error = reason)
            logger.info("配信できなかったので $nextAttemptAt に送り直す: ${row.username} → ${row.inbox} $reason")
        }
    }

    private companion object {
        const val BATCH_SIZE = 20
        val POLL_INTERVAL: Duration = 30.seconds
    }
}
