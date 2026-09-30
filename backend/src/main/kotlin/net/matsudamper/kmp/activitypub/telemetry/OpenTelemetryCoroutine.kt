package net.matsudamper.kmp.activitypub.telemetry

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement

suspend fun <T> withOpenTelemetryContext(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    block: suspend CoroutineScope.() -> T,
): T = withContext(dispatcher + Context.current().asContextElement(), block)

internal fun CoroutineDispatcher.withOpenTelemetryContext(): CoroutineContext =
    this + Context.current().asContextElement()
