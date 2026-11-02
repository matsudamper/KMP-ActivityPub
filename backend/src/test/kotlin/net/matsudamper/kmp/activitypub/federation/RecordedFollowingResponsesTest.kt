package net.matsudamper.kmp.activitypub.federation

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import net.matsudamper.kmp.activitypub.FakeRepositories

class RecordedFollowingResponsesTest {
    @Test
    fun responsesRecordMatchingValuesAndZeroMatchesAreErrors() {
        val exporter = InMemorySpanExporter.create()
        val provider = SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()
        OpenTelemetrySdk.builder().setTracerProvider(provider).build().use { telemetry ->
            val repositories = FakeRepositories()
            val following = repositories.followingRepository
            val actor = "https://remote.example/users/bob"
            listOf("alice", "carol").forEach { username ->
                val account = repositories.accountRepository.add(username, "hash", "type")
                following.add(account, actor, Instant.EPOCH)
                following.recordActivity(account, actor, "follow-1")
            }
            val responses = RecordedFollowingResponses(following, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), telemetry)
            val request = telemetry.getTracer("test").spanBuilder("inbox request").startSpan()
            request.makeCurrent().use {
                responses.accept("https://remote.example/@bob", "follow-1")
                responses.accept(actor, "old-follow")
                responses.accept(actor, "follow-1")
                responses.reject(actor, "follow-1")
                responses.reject(actor, "follow-1")
            }
            request.end()
            val spans = exporter.finishedSpanItems.filter { it.name.startsWith("Inbox.") }
            assertEquals(listOf(0L, 0L, 2L, 2L, 0L), spans.map { it.attributes.get(InboxSpan.AFFECTED_COUNT) })
            assertEquals(listOf(StatusCode.ERROR, StatusCode.ERROR, StatusCode.UNSET, StatusCode.UNSET, StatusCode.ERROR), spans.map { it.status.statusCode })
            assertEquals("https://remote.example/@bob", spans.first().attributes.get(InboxSpan.ACTOR))
            assertEquals("old-follow", spans[1].attributes.get(InboxSpan.FOLLOW_ID))
            spans.forEach { assertEquals(request.spanContext.spanId, it.parentSpanId) }
            listOf(spans[0], spans[1], spans[4]).forEach {
                assertEquals("no_matching_account_following", it.attributes.get(InboxSpan.DISCARD_REASON))
                assertEquals("一致する account_following が無い", it.status.description)
            }
        }
    }
}
