package net.matsudamper.kmp.activitypub.graphql

import kotlin.time.TimeSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactive.asFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.kickstart.tools.GraphQLResolver
import graphql.kickstart.tools.SchemaParser
import graphql.schema.DataFetchingEnvironment
import io.ktor.server.application.ApplicationCall
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.instrumentation.graphql.v20_0.GraphQLTelemetry
import net.matsudamper.kmp.activitypub.graphql.data.GraphQlRequest
import net.matsudamper.kmp.activitypub.telemetry.OpenTelemetryInitializer
import net.matsudamper.kmp.activitypub.telemetry.TraceLogger
import org.reactivestreams.Publisher
import org.slf4j.LoggerFactory

class GraphQlEngine private constructor(
    private val graphQl: GraphQL,
    private val createContext: (ApplicationCall) -> GraphQlContext,
    private val diContainer: DiContainer,
) {
    private val operationDurationHistogram: DoubleHistogram =
        OpenTelemetryInitializer
            .get()
            .getMeter("net.matsudamper.kmp.activitypub.backend")
            .histogramBuilder("graphql.operation.duration")
            .setDescription("GraphQLのOperationNameごとの合計処理時間")
            .setUnit("ms")
            .build()

    /**
     * @param uploads multipart で送られたファイル。同じ名前の変数に入れる
     */
    fun execute(
        request: GraphQlRequest,
        call: ApplicationCall,
        uploads: Map<String, GraphQlUpload>,
    ): JsonObject {
        val mark = TimeSource.Monotonic.markNow()
        val executionResult = graphQl.execute(executionInput(request, call, uploads))
        logErrors(executionResult)

        val response = toJsonObject(executionResult)

        operationDurationHistogram.record(
            mark.elapsedNow().inWholeMilliseconds.toDouble(),
            Attributes.of(
                AttributeKey.stringKey("graphql.operation.name"),
                request.operationName?.ifEmpty { null } ?: "anonymous",
            ),
        )

        return response
    }

    /**
     * subscription なら結果を流し続ける。query と mutation は結果を 1 つ流して終える
     */
    fun subscribe(
        request: GraphQlRequest,
        call: ApplicationCall,
    ): GraphQlSubscription {
        val executionResult = graphQl.execute(executionInput(request, call, mapOf()))
        logErrors(executionResult)

        val data: Any? = executionResult.getData()
        val publisher = data as? Publisher<*>
        return when {
            publisher != null -> GraphQlSubscription.Stream(
                publisher.asFlow().map { item ->
                    val itemResult = item as ExecutionResult
                    logErrors(itemResult)
                    toJsonObject(itemResult)
                },
            )

            executionResult.errors.isNotEmpty() && data == null -> {
                val errors = toJsonObject(executionResult)["errors"]
                check(errors is JsonArray) { "GraphQL のエラーが配列ではない" }
                GraphQlSubscription.Rejected(errors)
            }

            else -> GraphQlSubscription.Stream(flowOf(toJsonObject(executionResult)))
        }
    }

    private fun executionInput(
        request: GraphQlRequest,
        call: ApplicationCall,
        uploads: Map<String, GraphQlUpload>,
    ): ExecutionInput =
        ExecutionInput
            .newExecutionInput(request.query)
            .operationName(request.operationName)
            .variables(variablesOf(request) + uploads)
            .graphQLContext(mapOf(CONTEXT_KEY to createContext(call)))
            .graphQLContext(mapOf(DI_CONTAINER_KEY to diContainer))
            .build()

    private fun logErrors(executionResult: ExecutionResult) {
        executionResult.errors.forEach { error ->
            val dataFetchingException = (error as? ExceptionWhileDataFetching)?.exception
            if (dataFetchingException == null || dataFetchingException is GraphQlClientException) {
                logger.debug("GraphQL の実行に失敗した: {}", error.message)
            } else {
                TraceLogger.impl().noticeThrowable(dataFetchingException, isError = true)
            }
        }
    }

    private fun toJsonObject(executionResult: ExecutionResult): JsonObject {
        val response = GraphQlValues.toJsonElement(executionResult.toSpecification())
        check(response is JsonObject) { "GraphQL の結果がオブジェクトではない" }
        return response
    }

    private fun variablesOf(request: GraphQlRequest): Map<String, Any?> =
        request.variables
            ?.mapValues { (_, value) -> GraphQlValues.toRawValue(value) }
            .orEmpty()

    companion object {
        private val CONTEXT_KEY = Any()
        private val DI_CONTAINER_KEY = Any()

        private val logger = LoggerFactory.getLogger(GraphQlEngine::class.java)

        /**
         * alias で並べた数だけ処理するので上限を置く。画面の問い合わせは 10 未満
         */
        private const val MAX_QUERY_COMPLEXITY = 100

        private const val SCHEMA_DIRECTORY = "graphql"

        fun create(
            resolvers: List<GraphQLResolver<*>>,
            createContext: (ApplicationCall) -> GraphQlContext,
            diContainer: DiContainer,
        ): GraphQlEngine {
            val schema =
                SchemaParser
                    .newParser()
                    .schemaString(readSchema())
                    .resolvers(resolvers)
                    .scalars(GraphQlScalars.userId, GraphQlScalars.passkeyCredentialId, GraphQlScalars.noteImageId, GraphQlScalars.followingGroupId, GraphQlScalars.upload)
                    .build()
                    .makeExecutableSchema()

            return GraphQlEngine(
                graphQl = GraphQL
                    .newGraphQL(schema)
                    .instrumentation(
                        ChainedInstrumentation(
                            MaxQueryComplexityInstrumentation(MAX_QUERY_COMPLEXITY),
                            IdLoggerInstrumentation(TraceLogger.impl()),
                            GraphQLTelemetry
                                .builder(OpenTelemetryInitializer.get())
                                .setAddOperationNameToSpanName(true)
                                .setDataFetcherInstrumentationEnabled(true)
                                .build()
                                .createInstrumentation(),
                        ),
                    )
                    .build(),
                createContext = createContext,
                diContainer = diContainer,
            )
        }

        fun graphQlContext(env: DataFetchingEnvironment): GraphQlContext {
            return requireNotNull(env.graphQlContext.get(CONTEXT_KEY)) {
                "GraphQLContext に GraphQlContext が無い"
            }
        }

        fun diContainer(env: DataFetchingEnvironment): DiContainer {
            return requireNotNull(env.graphQlContext.get(DI_CONTAINER_KEY)) {
                "GraphQLContext に DiContainer が無い"
            }
        }

        private fun readSchema(): String =
            readResource("$SCHEMA_DIRECTORY/schema-list.txt")
                .lines()
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n") { readResource("$SCHEMA_DIRECTORY/$it") }

        private fun readResource(resource: String): String {
            val stream =
                GraphQlEngine::class.java.classLoader.getResourceAsStream(resource)
                    ?: throw IllegalStateException(
                        "$resource が見つからない。" +
                            ":backend:graphql が classpath にあるか、native バイナリなら resource-config.json を確かめること",
                    )

            return stream.use { it.readBytes().decodeToString() }
        }
    }
}

sealed interface GraphQlSubscription {
    class Stream(
        val results: Flow<JsonObject>,
    ) : GraphQlSubscription

    /**
     * 検証や認可で実行前に断った。結果を 1 つも流さない
     */
    class Rejected(
        val errors: JsonArray,
    ) : GraphQlSubscription
}
