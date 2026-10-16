package net.matsudamper.kmp.activitypub.graphql

import kotlinx.serialization.json.JsonObject
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.kickstart.tools.GraphQLResolver
import graphql.kickstart.tools.SchemaParser
import graphql.schema.DataFetchingEnvironment
import io.ktor.server.application.ApplicationCall
import net.matsudamper.kmp.activitypub.graphql.data.GraphQlRequest
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import org.slf4j.LoggerFactory

class GraphQlEngine private constructor(
    private val graphQl: GraphQL,
    private val createContext: (ApplicationCall) -> GraphQlContext,
    private val diContainer: DiContainer,
) {
    fun execute(
        request: GraphQlRequest,
        call: ApplicationCall,
    ): JsonObject {
        val input =
            ExecutionInput
                .newExecutionInput(request.query)
                .operationName(request.operationName)
                .variables(variablesOf(request))
                .graphQLContext(mapOf(CONTEXT_KEY to createContext(call)))
                .graphQLContext(mapOf(DI_CONTAINER_KEY to diContainer))
                .build()

        val executionResult = graphQl.execute(input)
        executionResult.errors.forEach { error ->
            val dataFetchingException = (error as? ExceptionWhileDataFetching)?.exception
            if (dataFetchingException == null || dataFetchingException is GraphQlClientException) {
                logger.debug("GraphQL の実行に失敗した: {}", error.message)
            } else {
                logger.error("GraphQL の実行に失敗した: {}", error.message, dataFetchingException)
            }
        }

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
                    .build()
                    .makeExecutableSchema()

            return GraphQlEngine(
                graphQl = GraphQL
                    .newGraphQL(schema)
                    .instrumentation(MaxQueryComplexityInstrumentation(MAX_QUERY_COMPLEXITY))
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

        fun passkeyService(env: DataFetchingEnvironment): PasskeyService =
            diContainer(env).passkeyService
                ?: throw GraphQlClientException("このサーバーではパスキーを使えない")

        /**
         * ディレクトリの列挙は native バイナリで効かない
         */
        private val SCHEMA_FILES =
            listOf(
                "directive.graphqls",
                "schema.graphqls",
                "user_mutation.graphqls",
            )

        private fun readSchema(): String = SCHEMA_FILES.joinToString(separator = "\n") { readResource("$SCHEMA_DIRECTORY/$it") }

        private const val SCHEMA_DIRECTORY = "graphql"

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
