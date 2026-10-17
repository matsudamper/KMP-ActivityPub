package net.matsudamper.kmp.activitypub.graphql

import kotlin.reflect.full.memberProperties
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.DataFetcher
import net.matsudamper.kmp.activitypub.telemetry.TraceLogger

internal class IdLoggerInstrumentation(
    private val tracer: TraceLogger,
) : Instrumentation {
    override fun instrumentDataFetcher(
        dataFetcher: DataFetcher<*>?,
        parameters: InstrumentationFieldFetchParameters?,
        state: InstrumentationState?,
    ): DataFetcher<*> =
        DataFetcher { environment ->
            val source: Any? = environment.getSource<Any>()
            if (source != null) {
                val id =
                    source::class
                        .memberProperties
                        .firstOrNull { it.name == "id" }
                        ?.getter
                        ?.call(source)
                        ?: runCatching {
                            source.javaClass.getMethod("getId").invoke(source)
                        }.getOrNull()

                if (id != null) {
                    tracer.setAttribute("graphql.id", id.toString())
                }
                tracer.setAttribute("graphql.members", source::class.memberProperties.joinToString { it.name })
            }

            dataFetcher?.get(environment)
        }
}
