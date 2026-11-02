package net.matsudamper.kmp.activitypub.graphql

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement

internal fun <T> otelSupplyAsync(supplier: () -> T): CompletableFuture<T> {
    val context = Context.current()
    return CompletableFuture.supplyAsync(
        { context.makeCurrent().use { supplier() } },
        ForkJoinPool.commonPool(),
    )
}

internal fun <T, U> CompletableFuture<T>.otelThenApplyAsync(fn: (T) -> U): CompletableFuture<U> {
    val context = Context.current()
    return this.thenApplyAsync(
        { t -> context.makeCurrent().use { fn(t) } },
        ForkJoinPool.commonPool(),
    )
}

internal fun <T> otelFuture(supplier: suspend () -> T): CompletableFuture<T> =
    CoroutineScope(Dispatchers.IO + Context.current().asContextElement()).future { supplier() }
