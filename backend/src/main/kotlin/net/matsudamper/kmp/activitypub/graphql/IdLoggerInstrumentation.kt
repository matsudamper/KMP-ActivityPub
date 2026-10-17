package net.matsudamper.kmp.activitypub.graphql

import java.lang.reflect.Modifier
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.DataFetcher
import net.matsudamper.kmp.activitypub.telemetry.TraceLogger

/**
 * kotlin-reflect のメタデータは native-image に載らないので Java のリフレクションで読む。
 * 型は GraphQlReflectionFeature がメソッドとフィールドを登録している
 */
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
                    source.javaClass.methods
                        .firstOrNull { it.name == "getId" && it.parameterCount == 0 }
                        ?.invoke(source)

                if (id != null) {
                    tracer.setAttribute("graphql.id", id.toString())
                }
                tracer.setAttribute(
                    "graphql.members",
                    source.javaClass.declaredFields
                        .filterNot { Modifier.isStatic(it.modifiers) }
                        .joinToString { it.name },
                )
            }

            dataFetcher?.get(environment)
        }
}
