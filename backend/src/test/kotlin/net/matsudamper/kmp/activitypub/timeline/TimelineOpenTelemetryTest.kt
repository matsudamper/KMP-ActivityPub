package net.matsudamper.kmp.activitypub.timeline

import java.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import net.matsudamper.kmp.activitypub.FakeRepositories
import net.matsudamper.kmp.activitypub.repository.TimelinePosition
import net.matsudamper.kmp.activitypub.repository.TimelinePost
import net.matsudamper.kmp.activitypub.repository.TimelineRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId
import net.matsudamper.kmp.activitypub.telemetry.withOpenTelemetryContext

class TimelineOpenTelemetryTest {
    @Test
    fun rebuiltCachedAndSqlReadsKeepTheRequestParentAndDescribeTheirSource() {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { telemetry ->
            val tracer = telemetry.getTracer("fake-instrumentation")
            val repository = FakeTimelineRepository()
            repository.positions.addAll((1..20).map { TimelinePosition(it.toLong(), "r$it") })
            val tracedRepository = object : TimelineRepository by repository {
                override fun rebuild(accountId: AccountId, groupId: FollowingGroupId?, limit: Int): List<TimelinePosition> {
                    tracer.spanBuilder("SQL rebuild").startSpan().end()
                    return repository.rebuild(accountId, groupId, limit)
                }
                override fun hydrate(accountId: AccountId, members: List<String>): List<TimelinePost> {
                    tracer.spanBuilder("SQL hydrate").startSpan().end()
                    return repository.hydrate(accountId, members)
                }
            }
            val store = FakeTimelineStore(repository.events)
            val tracedStore = TracedStore(store, tracer)
            val service = TimelineService(tracedRepository, FakeRepositories().followingGroupRepository, tracedStore, AccountEvents(tracedRepository), Clock.systemUTC(), telemetry)
            val request = tracer.spanBuilder("GraphQL homeTimeline").startSpan()
            request.makeCurrent().use {
                runBlocking {
                    withOpenTelemetryContext {
                        service.page(AccountId(1), null, null, 2)
                        service.page(AccountId(1), null, null, 2)
                        store.unavailable = true
                        service.page(AccountId(1), null, null, 2)
                    }
                }
            }
            request.end()
            val pages = exporter.finishedSpanItems.filter { it.name == "HomeTimeline.page" }
            assertEquals(3, pages.size)
            pages.forEach { assertEquals(request.spanContext.spanId, it.parentSpanId) }
            assertEquals(listOf(true, false, false), pages.map { it.attributes.get(TimelineSpan.REBUILT) })
            assertEquals(listOf(true, true, false), pages.map { it.attributes.get(TimelineSpan.CACHE_READ) })
            assertEquals(listOf(true, false, true), pages.map { it.attributes.get(TimelineSpan.SQL_READ) })
            val firstChildren = exporter.finishedSpanItems.filter { it.parentSpanId == pages.first().spanId }
            assertTrue(firstChildren.map { it.name }.containsAll(listOf("SQL rebuild", "SQL hydrate", "MGET", "ZADD", "ZRANGE")))
            firstChildren.forEach { assertEquals(request.spanContext.traceId, it.traceId) }
        }
    }

    private class TracedStore(private val store: TimelineStore, private val tracer: Tracer) : TimelineStore by store {
        override fun ready(feed: TimelineFeed): Boolean {
            redis("MGET")
            return store.ready(feed)
        }
        override fun appendRebuilt(feed: TimelineFeed, positions: List<TimelinePosition>) {
            redis("ZADD")
            store.appendRebuilt(feed, positions)
        }
        override fun read(feed: TimelineFeed, after: TimelinePosition?, limit: Int): List<TimelinePosition> {
            redis("ZRANGE")
            return store.read(feed, after, limit)
        }
        private fun redis(operation: String) {
            tracer.spanBuilder(operation).setAttribute("db.system.name", "redis").setAttribute("db.operation.name", operation).startSpan().end()
        }
    }
}
