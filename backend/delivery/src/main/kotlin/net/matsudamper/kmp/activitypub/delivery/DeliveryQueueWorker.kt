package net.matsudamper.kmp.activitypub.delivery

import java.time.Clock
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
 *
 * 同じ inbox のホスト宛ては取り出した順に 1 件ずつ送り、ホストをまたいでは同時に送る。
 * 相手は自分の inbox の応答を遅らせられるので、全体を 1 件ずつ送ると 1 つのホストが配信全体を待たせられる。
 * 送り終えていない行があるホスト宛ては新しく取り出さないので、遅いホストの行が手元に溜まり続けることもない
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
    private val sendPermits = Semaphore(SEND_CONCURRENCY)
    private val laneClosedSignal = Channel<Unit>(Channel.CONFLATED)
    private val inboxHostLanes = InboxHostLanes(
        onLaneClosed = {
            laneClosedSignal.trySend(Unit)
            // 送り終えたホスト宛てに、取り出さずにおいた行が残っていることがある
            wakeUpSignal.trySend(Unit)
        },
    )

    fun wakeUp() {
        wakeUpSignal.trySend(Unit)
    }

    suspend fun run() {
        coroutineScope {
            val sending = SupervisorJob(coroutineContext[Job])
            val sendingScope = CoroutineScope(coroutineContext + sending)
            recoverDelivering()
            while (true) {
                val hasMore =
                    try {
                        val claimed = claim()
                        inboxHostLanes.enqueue(sendingScope, claimed, ::sendWithPermit)
                        claimed.size >= BATCH_SIZE
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.warn("配信キューを処理できなかった", e)
                        // 送っている行を巻き戻すと二重に送るので、送り終わるのを待ってから戻す
                        sending.children.toList().joinAll()
                        recoverDelivering()
                        false
                    }
                if (hasMore) {
                    // 取り出した行は送り終えるまで手元に持つので、送っているホストの数で次を取り出すのを止める
                    while (inboxHostLanes.activeCount >= MAX_ACTIVE_HOSTS) laneClosedSignal.receive()
                } else {
                    withTimeoutOrNull(POLL_INTERVAL) { wakeUpSignal.receive() }
                }
            }
        }
    }

    /**
     * 期限の来たものを取り出して、送り終えるまで待つ
     *
     * @return 期限の来たものがまだ残っていそうなら true
     */
    suspend fun deliverDue(): Boolean {
        val claimed = claim()
        coroutineScope {
            inboxHostLanes.enqueue(this, claimed, ::sendWithPermit)
        }
        return claimed.size >= BATCH_SIZE
    }

    private suspend fun sendWithPermit(row: ClaimedDelivery) {
        sendPermits.withPermit { deliverInSpan(row) }
    }

    private suspend fun deliverInSpan(row: ClaimedDelivery) {
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
                try {
                    withOpenTelemetryContext { recordFailure(row, reason = e.toString(), retryable = true) }
                } catch (recordingFailure: Exception) {
                    if (recordingFailure is CancellationException) throw recordingFailure
                    logger.warn("配信の失敗を記録できなかったので、次の起動まで送り直さない: ${row.username} → ${row.inbox}", recordingFailure)
                }
            }
        }
    }

    private suspend fun claim(): List<ClaimedDelivery> {
        val startedAt = clock.instant()
        val claimed = try {
            withoutPollingSpans {
                withOpenTelemetryContext { queue.claim(now = startedAt, limit = BATCH_SIZE, excludedInboxHosts = inboxHostLanes.activeHosts) }
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
            withOpenTelemetryContext { queue.giveUp(row.id, error = "送り主の鍵が無い", failedAt = clock.instant()) }
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
            queue.giveUp(row.id, error = reason, failedAt = clock.instant())
            logger.warn("配信を諦めた: ${row.username} → ${row.inbox} 試行=${row.attempts} 回 $reason")
        } else {
            DeliverySpan.outcome("retry_scheduled")
            queue.scheduleRetry(row.id, nextAttemptAt = nextAttemptAt, error = reason)
            logger.info("配信できなかったので $nextAttemptAt に送り直す: ${row.username} → ${row.inbox} $reason")
        }
    }

    private companion object {
        const val BATCH_SIZE = 20
        const val SEND_CONCURRENCY = 8
        const val MAX_ACTIVE_HOSTS = 32
        val POLL_INTERVAL: Duration = 30.seconds
    }
}
