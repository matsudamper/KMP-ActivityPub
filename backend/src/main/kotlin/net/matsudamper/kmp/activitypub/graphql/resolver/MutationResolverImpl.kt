package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.model.MutationResolver
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminMutation
import net.matsudamper.kmp.activitypub.graphql.model.QlUserMutation

class MutationResolverImpl : MutationResolver {
    override fun admin(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlAdminMutation>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlAdminMutation()).build())
    }

    override fun user(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlUserMutation>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlUserMutation()).build())
    }
}
