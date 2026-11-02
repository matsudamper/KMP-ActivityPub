package net.matsudamper.kmp.activitypub.telemetry

import java.util.UUID
import kotlinx.coroutines.withContext
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement

internal const val POLLING_TRACE_STATE_KEY = "activitypub-polling"

suspend fun <T> withoutPollingSpans(block: suspend () -> T): T {
    val traceId = UUID.randomUUID().toString().replace("-", "")
    val spanContext = SpanContext.create(
        traceId,
        traceId.take(16),
        TraceFlags.getDefault(),
        TraceState.builder().put(POLLING_TRACE_STATE_KEY, "true").build(),
    )
    val context = Context.root().with(Span.wrap(spanContext))
    return withContext(context.asContextElement()) { block() }
}
