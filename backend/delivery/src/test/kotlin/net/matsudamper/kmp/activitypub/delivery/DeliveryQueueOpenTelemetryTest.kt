package net.matsudamper.kmp.activitypub.delivery

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.samplers.Sampler
import net.matsudamper.kmp.activitypub.repository.ClaimedDelivery
import net.matsudamper.kmp.activitypub.repository.DeliveryQueueRepository
import net.matsudamper.kmp.activitypub.repository.EnqueueNoteResult
import net.matsudamper.kmp.activitypub.repository.NotePost
import net.matsudamper.kmp.activitypub.repository.ProfileUpdate
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.telemetry.FilteringSpanExporter
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.ActorUrls
import net.matsudamper.kotpub.actor.LocalActor
import net.matsudamper.kotpub.crypto.RsaKeys
import net.matsudamper.kotpub.delivery.ActivityDelivery
import net.matsudamper.kotpub.delivery.DeliveryResult

class DeliveryQueueOpenTelemetryTest {
    @Test
    fun deliveredRowOwnsSenderHttpAndDatabaseSpans() = runBlocking {
        withTelemetry { telemetry, exporter ->
            val fixture = Fixture(telemetry)
            fixture.queue.rows = listOf(ROW)
            fixture.worker.deliverDue()
            val spans = exporter.finishedSpanItems
            val delivery = spans.single { it.name == "DeliveryQueue.deliver" }
            assertFalse(delivery.parentSpanContext.isValid)
            assertEquals(ROW.id, delivery.attributes.get(DeliverySpan.ID))
            assertEquals(ROW.kind, delivery.attributes.get(DeliverySpan.KIND))
            assertEquals(ROW.username, delivery.attributes.get(DeliverySpan.SENDER))
            assertEquals(ROW.inbox, delivery.attributes.get(DeliverySpan.INBOX))
            assertEquals(ROW.attempts.toLong(), delivery.attributes.get(DeliverySpan.ATTEMPTS))
            assertEquals("delivered", delivery.attributes.get(DeliverySpan.OUTCOME))
            val children = spans.filter { it.parentSpanId == delivery.spanId }
            assertEquals(setOf("SQL sender keys", "HTTP POST", "SQL markDelivered"), children.map { it.name }.toSet())
            children.forEach { assertEquals(delivery.traceId, it.traceId) }
            val claim = spans.single { it.name == "DeliveryQueue.claim" }
            assertFalse(claim.parentSpanContext.isValid)
            assertEquals(1L, claim.attributes.get(DeliverySpan.CLAIMED_COUNT))
            assertTrue(spans.none { it.name == "SQL claim" })
        }
    }

    @Test
    fun emptyClaimsExportNeitherPollingSqlNorRootSpansEvenWithAlwaysOnSampler() = runBlocking {
        withTelemetry { telemetry, exporter ->
            val fixture = Fixture(telemetry)
            repeat(3) { assertFalse(fixture.worker.deliverDue()) }
            assertTrue(exporter.finishedSpanItems.isEmpty())
        }
    }

    @Test
    fun retriesRejectionsAndMissingKeysStayWithinTheirDeliverySpan() = runBlocking {
        withTelemetry { telemetry, exporter ->
            listOf("retry", "reject", "missing", "exception").forEach { scenario ->
                val fixture = Fixture(telemetry)
                fixture.queue.rows = listOf(ROW)
                fixture.missingSender = scenario == "missing"
                fixture.sendFailure = scenario == "exception"
                fixture.result = DeliveryResult.Failed("相手の応答", scenario == "retry")
                fixture.worker.deliverDue()
                val spans = exporter.finishedSpanItems
                val delivery = spans.single { it.name == "DeliveryQueue.deliver" }
                val retry = scenario in listOf("retry", "exception")
                val outcome = when (scenario) {
                    "retry", "exception" -> "retry_scheduled"
                    "missing" -> "gave_up.no_account"
                    else -> "gave_up.rejected"
                }
                assertEquals(outcome, delivery.attributes.get(DeliverySpan.OUTCOME))
                assertEquals(StatusCode.ERROR, delivery.status.statusCode)
                assertTrue(delivery.attributes.get(DeliverySpan.FAILURE_REASON).orEmpty().isNotBlank())
                val record = spans.single { it.name == if (retry) "SQL scheduleRetry" else "SQL giveUp" }
                assertEquals(delivery.spanId, record.parentSpanId)
                exporter.reset()
            }
        }
    }

    @Test
    fun startupAndFailureRecoveryHaveTheirOwnRootAndDatabaseChild() = runBlocking {
        withTelemetry { telemetry, exporter ->
            val fixture = Fixture(telemetry)
            fixture.queue.failNextClaim = true
            val running = launch { fixture.worker.run() }
            try {
                withTimeout(5000) { fixture.queue.recoveredAfterFailure.await() }
            } finally {
                running.cancelAndJoin()
            }
            val spans = exporter.finishedSpanItems
            val recoveries = spans.filter { it.name == "DeliveryQueue.recoverDelivering" }
            assertEquals(2, recoveries.size)
            recoveries.forEach { recovery ->
                assertFalse(recovery.parentSpanContext.isValid)
                assertTrue(spans.any { it.name == "SQL recoverDelivering" && it.parentSpanId == recovery.spanId })
            }
            val claim = spans.single { it.name == "DeliveryQueue.claim" }
            assertEquals("failed", claim.attributes.get(DeliverySpan.OUTCOME))
            assertEquals(StatusCode.ERROR, claim.status.statusCode)
        }
    }

    private suspend fun withTelemetry(block: suspend (OpenTelemetry, InMemorySpanExporter) -> Unit) {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder().setSampler(Sampler.alwaysOn())
            .addSpanProcessor(SimpleSpanProcessor.create(FilteringSpanExporter(exporter))).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { telemetry -> block(telemetry, exporter) }
    }

    private class Fixture(telemetry: OpenTelemetry) {
        private val tracer = telemetry.getTracer("fake-instrumentation")
        val queue = Queue(tracer)
        var result: DeliveryResult = DeliveryResult.Delivered
        var missingSender = false
        var sendFailure = false
        val worker = DeliveryQueueWorker(
            queue,
            DeliverySenders {
                tracer.spanBuilder("SQL sender keys").startSpan().end()
                if (missingSender) null else SENDER
            },
            object : ActivityDelivery {
                override suspend fun deliver(inbox: String, sender: LocalActor, body: ByteArray): DeliveryResult {
                    tracer.spanBuilder("HTTP POST").startSpan().end()
                    check(!sendFailure) { "送信失敗" }
                    return result
                }
                override fun close() = Unit
            },
            DeliveryRetryPolicy(30.seconds, 24.hours, 7.days),
            Clock.fixed(NOW, ZoneOffset.UTC),
            telemetry,
        )
    }

    private class Queue(private val tracer: Tracer) : DeliveryQueueRepository {
        var rows = listOf<ClaimedDelivery>()
        var failNextClaim = false
        val recoveredAfterFailure = CompletableDeferred<Unit>()
        private var recoveries = 0

        override fun claim(now: Instant, limit: Int): List<ClaimedDelivery> {
            tracer.spanBuilder("SQL claim").startSpan().end()
            if (failNextClaim) {
                failNextClaim = false
                error("claim失敗")
            }
            val claimed = rows
            rows = listOf()
            return claimed
        }
        override fun markDelivered(id: Long, deliveredAt: Instant) {
            tracer.spanBuilder("SQL markDelivered").startSpan().end()
        }
        override fun scheduleRetry(id: Long, nextAttemptAt: Instant, error: String) {
            tracer.spanBuilder("SQL scheduleRetry").startSpan().end()
        }
        override fun giveUp(id: Long, error: String) {
            tracer.spanBuilder("SQL giveUp").startSpan().end()
        }
        override fun recoverDelivering(now: Instant): Int {
            tracer.spanBuilder("SQL recoverDelivering").startSpan().end()
            recoveries++
            if (recoveries == 2) recoveredAfterFailure.complete(Unit)
            return 0
        }
        override fun enqueueFollow(accountId: AccountId, inbox: String, body: String, now: Instant) = error("未使用")
        override fun cancelPendingFollowAndEnqueueUndo(accountId: AccountId, inbox: String, followId: String, body: String, now: Instant) = error("未使用")
        override fun enqueueNote(post: NotePost): EnqueueNoteResult = error("未使用")
        override fun updateProfile(update: ProfileUpdate): Boolean = error("未使用")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-01T00:00:00Z")
        val ROW = ClaimedDelivery(1, "alice", "https://remote.example/inbox", "body", 1, NOW, "follow")
        val SENDER = LocalActor(ActorUrls("local.example", "alice"), ActorPrivateKey(RsaKeys.generateKeyPair().private, ActorPrivateKey.Origin.Environment))
    }
}
