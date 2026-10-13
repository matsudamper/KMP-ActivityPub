package net.matsudamper.kmp.activitypub.graphql

import kotlinx.serialization.json.JsonObject
import graphql.ExceptionWhileDataFetching
import graphql.ExecutionInput
import graphql.GraphQL
import graphql.analysis.MaxQueryComplexityInstrumentation
import graphql.kickstart.tools.GraphQLResolver
import graphql.kickstart.tools.SchemaParser
import net.matsudamper.kmp.activitypub.graphql.data.GraphQlRequest
import org.slf4j.LoggerFactory

class GraphQlEngine private constructor(
    private val graphQl: GraphQL,
) {
    fun execute(request: GraphQlRequest): JsonObject {
        val input =
            ExecutionInput
                .newExecutionInput(request.query)
                .operationName(request.operationName)
                .variables(variablesOf(request))
                .build()

        val executionResult = graphQl.execute(input)
        executionResult.errors.forEach { error ->
            val dataFetchingException = (error as? ExceptionWhileDataFetching)?.exception
            if (dataFetchingException == null) {
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
        private val logger = LoggerFactory.getLogger(GraphQlEngine::class.java)

        /**
         * alias で並べた数だけ処理するので上限を置く。画面の問い合わせは 10 未満
         */
        private const val MAX_QUERY_COMPLEXITY = 100

        fun create(resolvers: List<GraphQLResolver<*>>): GraphQlEngine {
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
            )
        }

        /**
         * ディレクトリの列挙は native バイナリで効かない
         */
        private fun readSchema(): String {
            val fileNames =
                readResource(SCHEMA_LIST_RESOURCE)
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()

            check(fileNames.isNotEmpty()) { "$SCHEMA_LIST_RESOURCE が空。スキーマが 1 つも無い" }

            return fileNames.joinToString(separator = "\n") { readResource("$SCHEMA_DIRECTORY/$it") }
        }

        private const val SCHEMA_DIRECTORY = "graphql"
        private const val SCHEMA_LIST_RESOURCE = "$SCHEMA_DIRECTORY/schema-list.txt"

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
