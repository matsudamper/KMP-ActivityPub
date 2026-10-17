package net.matsudamper.kmp.activitypub.telemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.runtimetelemetry.RuntimeTelemetry
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk

/**
 * 送り先などは OTEL_* の環境変数で指定する
 */
class OpenTelemetryInitializer private constructor(
    private val sdk: OpenTelemetrySdk,
    private val runtimeTelemetry: RuntimeTelemetry,
) : AutoCloseable {
    val openTelemetry: OpenTelemetry get() = sdk

    override fun close() {
        runtimeTelemetry.close()
        sdk.close()
    }

    companion object {
        fun initialize(): OpenTelemetryInitializer {
            // 新仕様で出力する
            System.setProperty("otel.semconv-stability.opt-in", "database")

            val sdk =
                AutoConfiguredOpenTelemetrySdk
                    .builder()
                    .addPropertiesSupplier {
                        mapOf(
                            "otel.exporter.otlp.protocol" to "http/protobuf",
                        )
                    }.addSpanExporterCustomizer { exporter, _ -> FilteringSpanExporter(exporter) }
                    .build()
                    .openTelemetrySdk

            return OpenTelemetryInitializer(
                sdk = sdk,
                runtimeTelemetry = RuntimeTelemetry.create(sdk),
            )
        }
    }
}
