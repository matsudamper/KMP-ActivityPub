package net.matsudamper.kmp.activitypub.telemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * 初期化とポーリングのコマンドは大量のルートspanになる。取得できたclaimと配信処理は別のspanで追う。
 */
class FilteringSpanExporter(
    private val delegate: SpanExporter,
) : SpanExporter {
    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val exportTargets = spans.filterNot {
            it.isUnexportedRedisCommand() || it.spanContext.traceState.get(POLLING_TRACE_STATE_KEY) == "true"
        }
        if (exportTargets.isEmpty()) return CompletableResultCode.ofSuccess()

        return delegate.export(exportTargets)
    }

    override fun flush(): CompletableResultCode = delegate.flush()

    override fun shutdown(): CompletableResultCode = delegate.shutdown()

    private fun SpanData.isUnexportedRedisCommand(): Boolean =
        attributes.get(DB_SYSTEM_NAME) == "redis" &&
            attributes.get(DB_OPERATION_NAME) in UNEXPORTED_REDIS_OPERATIONS

    private companion object {
        val DB_SYSTEM_NAME: AttributeKey<String> = AttributeKey.stringKey("db.system.name")
        val DB_OPERATION_NAME: AttributeKey<String> = AttributeKey.stringKey("db.operation.name")
        val UNEXPORTED_REDIS_OPERATIONS = setOf("CLIENT", "HELLO", "SELECT", "SETNAME", "XGROUP", "XAUTOCLAIM", "XREADGROUP")
    }
}
