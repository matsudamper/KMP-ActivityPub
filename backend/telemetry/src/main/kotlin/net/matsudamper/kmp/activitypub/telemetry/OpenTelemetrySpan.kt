package net.matsudamper.kmp.activitypub.telemetry

import kotlinx.coroutines.withContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement

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

suspend fun <T> inOpenTelemetrySuspendSpan(tracer: Tracer, name: String, block: suspend (Span) -> T): T {
    val span = tracer.spanBuilder(name).startSpan()
    try {
        return withContext(Context.current().with(span).asContextElement()) { block(span) }
    } catch (failure: Exception) {
        span.recordException(failure)
        span.setStatus(StatusCode.ERROR)
        throw failure
    } finally {
        span.end()
    }
}
