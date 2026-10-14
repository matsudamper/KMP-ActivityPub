package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.model.QueryResolver

class QueryResolverImpl : QueryResolver {
    override fun unused(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<Boolean?>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<Boolean?>(null).build())
    }
}
