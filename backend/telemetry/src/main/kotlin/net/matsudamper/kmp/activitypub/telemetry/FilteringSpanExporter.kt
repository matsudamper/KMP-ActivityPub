package net.matsudamper.kmp.activitypub.telemetry

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.sdk.trace.export.SpanExporter

/**
 * Lettuce が接続時に自分で送る初期化コマンドは呼び出し元と紐づかず、孤立したルートスパンになるので送らない
 */
internal class FilteringSpanExporter(
    private val delegate: SpanExporter,
) : SpanExporter {
    override fun export(spans: Collection<SpanData>): CompletableResultCode {
        val exportTargets = spans.filterNot { it.isLettuceConnectionInitialization() }
        if (exportTargets.isEmpty()) return CompletableResultCode.ofSuccess()

        return delegate.export(exportTargets)
    }

    override fun flush(): CompletableResultCode = delegate.flush()

    override fun shutdown(): CompletableResultCode = delegate.shutdown()

    private fun SpanData.isLettuceConnectionInitialization(): Boolean =
        attributes.get(DB_SYSTEM_NAME) == "redis" &&
            attributes.get(DB_OPERATION_NAME) in LETTUCE_CONNECTION_INITIALIZATION_OPERATIONS

    private companion object {
        val DB_SYSTEM_NAME: AttributeKey<String> = AttributeKey.stringKey("db.system.name")
        val DB_OPERATION_NAME: AttributeKey<String> = AttributeKey.stringKey("db.operation.name")
        val LETTUCE_CONNECTION_INITIALIZATION_OPERATIONS = setOf("CLIENT", "HELLO", "SELECT", "SETNAME")
    }
}
