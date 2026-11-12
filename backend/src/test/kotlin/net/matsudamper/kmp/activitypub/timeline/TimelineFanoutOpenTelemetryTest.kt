package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.telemetry.FilteringSpanExporter
import net.matsudamper.kmp.activitypub.repository.TimelineRecipient

class TimelineFanoutOpenTelemetryTest {
    @Test
    fun jobOwnsDatabasePipelineAndAcknowledgementSpansAndCounts() = withTelemetry { telemetry, exporter ->
        val tracer = telemetry.getTracer("fake-instrumentation")
        val repository = object : TimelineRepository by FakeTimelineRepository() {
            override fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<TimelineRecipient> {
                tracer.spanBuilder("SQL recipients").startSpan().end()
                return listOf(TimelineRecipient(AccountId(1), null), TimelineRecipient(AccountId(2), null))
            }
        }
        val store = InstrumentedStore(tracer)
        store.pending = listOf(FanoutJob("1-0", TimelinePosition(1, "r1")))
        val worker = TimelineFanoutWorker(repository, store, AccountEvents(repository), Clock.systemUTC(), telemetry)
        assertEquals(1, worker.runOnce(false))
        val spans = exporter.finishedSpanItems
        val job = spans.single { it.name == "TimelineFanout.distribute" }
        assertFalse(job.parentSpanContext.isValid)
        assertEquals("r1", job.attributes.get(TimelineSpan.MEMBER))
        assertEquals(1L, job.attributes.get(TimelineSpan.DELIVERED_COUNT))
        assertEquals(1L, job.attributes.get(TimelineSpan.SKIPPED_COUNT))
        assertEquals("delivered", job.attributes.get(TimelineSpan.OUTCOME))
        val children = spans.filter { it.spanId != job.spanId }
        assertEquals(setOf("SQL recipients", "ZRANGE", "ZADD", "MULTI", "XACK", "XDEL", "EXEC"), children.map { it.name }.toSet())
        children.forEach {
            assertEquals(job.spanId, it.parentSpanId)
            assertEquals(job.traceId, it.traceId)
        }
    }

    @Test
    fun emptyPollingAndGroupInitializationDoNotExportSpans() = withTelemetry { telemetry, exporter ->
        val store = InstrumentedStore(telemetry.getTracer("fake-instrumentation"))
        store.initializeGroup()
        val worker = TimelineFanoutWorker(FakeTimelineRepository(), store, AccountEvents(FakeTimelineRepository()), Clock.systemUTC(), telemetry)
        repeat(3) { assertEquals(0, worker.runOnce(true)) }
        assertTrue(exporter.finishedSpanItems.isEmpty())
    }

    private fun withTelemetry(block: (OpenTelemetry, InMemorySpanExporter) -> Unit) {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(FilteringSpanExporter(exporter))).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { telemetry ->
            block(telemetry, exporter)
        }
    }

    private class InstrumentedStore(private val tracer: Tracer) : TimelineStore by FakeTimelineStore(mutableListOf()) {
        var pending = listOf<FanoutJob>()

        override fun initializeGroup() = redis("XGROUP")

        override fun jobs(consumer: String, block: Boolean): List<FanoutJob> {
            redis("XAUTOCLAIM")
            redis("XREADGROUP")
            return pending
        }

        override fun distribute(feeds: List<TimelineFeed>, position: TimelinePosition): Int {
            redis("ZRANGE")
            redis("ZADD")
            return 1
        }

        override fun acknowledge(id: String) {
            listOf("MULTI", "XACK", "XDEL", "EXEC").forEach { redis(it) }
            pending = listOf()
        }

        private fun redis(operation: String) {
            tracer.spanBuilder(operation)
                .setAttribute("db.system.name", "redis")
                .setAttribute("db.operation.name", operation).startSpan().end()
        }
    }
}
