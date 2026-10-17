package net.matsudamper.kmp.activitypub.graphql

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ForkJoinPool
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.scheduler.BatchLoaderScheduler

internal class OtelBatchLoaderScheduler(
    private val otelContext: Context,
    private val spanName: String,
) : BatchLoaderScheduler {
    private val tracer = OpenTelemetryInitializer.get().getTracer("graphql-dataloader")

    override fun <K, V> scheduleBatchLoader(
        scheduledCall: BatchLoaderScheduler.ScheduledBatchLoaderCall<V>,
        keys: List<K>,
        environment: BatchLoaderEnvironment,
    ): CompletableFuture<List<V>> =
        CompletableFuture.supplyAsync(
            { withSpan { scheduledCall.invoke().toCompletableFuture().join() } },
            otelContext.wrap(ForkJoinPool.commonPool()),
        )

    override fun <K, V> scheduleMappedBatchLoader(
        scheduledCall: BatchLoaderScheduler.ScheduledMappedBatchLoaderCall<K, V>,
        keys: List<K>,
        environment: BatchLoaderEnvironment,
    ): CompletableFuture<Map<K, V>> =
        CompletableFuture.supplyAsync(
            { withSpan { scheduledCall.invoke().toCompletableFuture().join() } },
            otelContext.wrap(ForkJoinPool.commonPool()),
        )

    override fun <K> scheduleBatchPublisher(
        scheduledCall: BatchLoaderScheduler.ScheduledBatchPublisherCall,
        keys: List<K>,
        environment: BatchLoaderEnvironment,
    ) {
        otelContext.makeCurrent().use {
            withSpan { scheduledCall.invoke() }
        }
    }

    private fun <T> withSpan(block: () -> T): T {
        val span = tracer.spanBuilder(spanName).startSpan()
        try {
            return span.makeCurrent().use { block() }
        } catch (e: Exception) {
            span.setStatus(StatusCode.ERROR)
            span.recordException(e)
            throw e
        } finally {
            span.end()
        }
    }
}
