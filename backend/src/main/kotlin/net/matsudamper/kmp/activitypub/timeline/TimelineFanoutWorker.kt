package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Span
import net.matsudamper.kmp.activitypub.repository.TimelineCachePolicy
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.inOpenTelemetrySpan
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext
import org.slf4j.LoggerFactory

class TimelineFanoutWorker(
    private val repository: TimelineRepository,
    private val store: TimelineStore,
    private val events: AccountEvents,
    private val clock: Clock,
    openTelemetry: OpenTelemetry = OpenTelemetryInitializer.get(),
) {
    private val tracer = openTelemetry.getTracer("activitypub-timeline")
    private val logger = LoggerFactory.getLogger(TimelineFanoutWorker::class.java)
    private val consumer = System.getenv("HOSTNAME") ?: UUID.randomUUID().toString()

    suspend fun run() {
        while (true) {
            try {
                withOpenTelemetryContext { store.initializeGroup() }
                break
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                logger.warn("タイムライン配布のコンシューマーグループを作れなかった", failure)
                delay(1000)
            }
        }
        var block = true
        while (true) {
            try {
                val count = withOpenTelemetryContext { runOnce(block) }
                block = count < 100
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                logger.warn("タイムライン配布に失敗した。未 ACK のジョブは再取得する", failure)
                delay(1000)
            }
        }
    }

    fun runOnce(block: Boolean): Int {
        val jobs = try {
            store.jobs(consumer, block)
        } catch (failure: Exception) {
            if (failure.message?.contains("NOGROUP") != true) throw failure
            store.initializeGroup()
            store.jobs(consumer, block)
        }
        jobs.forEach { job ->
            inOpenTelemetrySpan(tracer, "TimelineFanout.distribute", true) { span ->
                span.setAttribute(TimelineSpan.MEMBER, job.position.member)
                span.setAttribute(TimelineSpan.DELIVERED_COUNT, 0)
                span.setAttribute(TimelineSpan.SKIPPED_COUNT, 0)
                try {
                    distribute(job, span)
                    store.acknowledge(job.id)
                    span.setAttribute(TimelineSpan.OUTCOME, "delivered")
                } catch (failure: Exception) {
                    span.setAttribute(TimelineSpan.OUTCOME, "failed")
                    throw failure
                }
            }
        }
        return jobs.size
    }

    private fun distribute(job: FanoutJob, span: Span) {
        var after: AccountId? = null
        var delivered = 0L
        var skipped = 0L
        do {
            val recipients = repository.recipients(job.position.member, clock.instant().minusSeconds(TimelineCachePolicy.FANOUT_ACTIVE_WINDOW_SECONDS), after, 1000)
            val distributed = store.distribute(recipients.map { TimelineFeed(it.accountId, it.groupId) }, job.position)
            events.publishArrival(recipients.map { it.accountId }, job.position)
            delivered += distributed
            skipped += recipients.size - distributed
            span.setAttribute(TimelineSpan.DELIVERED_COUNT, delivered)
            span.setAttribute(TimelineSpan.SKIPPED_COUNT, skipped)
            after = recipients.lastOrNull()?.accountId
        } while (recipients.size == 1000)
    }
}
