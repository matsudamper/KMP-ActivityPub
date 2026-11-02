package net.matsudamper.kmp.activitypub.telemetry

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor

class OpenTelemetryCoroutineTest {
    @Test
    fun ioAndSuspensionKeepTheRequestParent() {
        val exporter = InMemorySpanExporter.create()
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build().use { provider ->
            val tracer = provider.get("test")
            val request = tracer.spanBuilder("request").startSpan()
            request.makeCurrent().use {
                runBlocking {
                    withOpenTelemetryContext {
                        delay(1)
                        tracer.spanBuilder("database").startSpan().end()
                    }
                }
            }
            request.end()
            val child = exporter.finishedSpanItems.single { it.name == "database" }
            assertEquals(request.spanContext.spanId, child.parentSpanId)
            assertEquals(request.spanContext.traceId, child.traceId)
        }
    }
}
