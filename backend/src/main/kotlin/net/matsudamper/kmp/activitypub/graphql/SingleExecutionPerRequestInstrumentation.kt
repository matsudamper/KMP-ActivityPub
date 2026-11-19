package net.matsudamper.kmp.activitypub.graphql

import graphql.ExecutionResult
import graphql.execution.AbortExecutionException
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters

/**
 * alias で並べると並列に実行されるので、resolver の実行前に弾く。
 * QueryTraverser は同じ fragment を複数箇所で展開しても 1 回しか走査しないので、正規化した操作で数える
 */
internal class SingleExecutionPerRequestInstrumentation : Instrumentation {
    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters,
        state: InstrumentationState?,
    ): InstrumentationContext<ExecutionResult>? {
        val executionContext = parameters.executionContext
        val schema = executionContext.graphQLSchema
        val restrictedFieldCount =
            executionContext.normalizedQueryTree.get()
                .coordinatesToNormalizedFields
                .entries()
                .count { (coordinates, _) ->
                    schema.getFieldDefinition(coordinates)?.getAppliedDirective(DIRECTIVE_NAME) != null
                }
        if (restrictedFieldCount > 1) {
            throw AbortExecutionException("@$DIRECTIVE_NAME のフィールドは 1 リクエストにつき 1 回まで")
        }
        return super.beginExecuteOperation(parameters, state)
    }

    private companion object {
        const val DIRECTIVE_NAME = "singleExecutionPerRequest"
    }
}
