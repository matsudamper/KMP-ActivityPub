package net.matsudamper.kmp.activitypub.telemetry

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement

suspend fun <T> withOpenTelemetryContext(block: suspend CoroutineScope.() -> T): T =
    withContext(Dispatchers.IO + Context.current().asContextElement(), block)
