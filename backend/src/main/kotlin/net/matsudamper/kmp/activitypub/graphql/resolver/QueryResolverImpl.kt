package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserQuery
import net.matsudamper.kmp.activitypub.graphql.model.QueryResolver

class QueryResolverImpl : QueryResolver {
    override fun admin(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlAdminQuery>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlAdminQuery()).build())
    }

    override fun user(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlUserQuery>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlUserQuery()).build())
    }
}
