package net.matsudamper.kmp.activitypub.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

/**
 * 送り先などは OTEL_* の環境変数で指定する
 */
object OpenTelemetryInitializer {
    private var sdk: OpenTelemetry? = null

    fun initialize(): OpenTelemetry {
        // 新仕様で出力する
        System.setProperty("otel.semconv-stability.opt-in", "database")

        val openTelemetry =
            AutoConfiguredOpenTelemetrySdk
                .builder()
                .addPropertiesSupplier {
                    mapOf(
                        "otel.exporter.otlp.protocol" to "http/protobuf",
                    )
                }.addSpanExporterCustomizer { exporter, _ -> FilteringSpanExporter(exporter) }
                .build()
                .openTelemetrySdk

        val runtimeTelemetry = RuntimeTelemetry.create(openTelemetry)

        sdk = openTelemetry

        Runtime.getRuntime().addShutdownHook(Thread { runtimeTelemetry.close() })
        Runtime.getRuntime().addShutdownHook(Thread { openTelemetry.close() })

        return openTelemetry
    }

    /**
     * テストでは initialize せずに使うので、未初期化なら何もしない実装を返す
     */
    fun get(): OpenTelemetry = sdk ?: OpenTelemetry.noop()
}
