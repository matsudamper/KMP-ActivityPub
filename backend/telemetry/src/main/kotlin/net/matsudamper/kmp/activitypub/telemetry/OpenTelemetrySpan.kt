package net.matsudamper.kmp.activitypub.telemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer

fun <T> inOpenTelemetrySpan(tracer: Tracer, name: String, root: Boolean, block: (Span) -> T): T {
    val builder = tracer.spanBuilder(name)
    if (root) builder.setNoParent()
    val span = builder.startSpan()
    try {
        return span.makeCurrent().use { block(span) }
    } catch (failure: Exception) {
        span.recordException(failure)
        span.setStatus(StatusCode.ERROR)
        throw failure
    } finally {
        span.end()
    }
}
