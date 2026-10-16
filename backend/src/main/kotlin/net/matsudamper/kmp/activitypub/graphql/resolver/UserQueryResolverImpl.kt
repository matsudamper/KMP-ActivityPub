package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlUserQuery
import net.matsudamper.kmp.activitypub.graphql.model.UserQueryResolver

class UserQueryResolverImpl : UserQueryResolver {
    override fun loggedIn(
        userQuery: QlUserQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Boolean>> {
        val loggedIn = GraphQlEngine.graphQlContext(env).userAccountId() != null
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(loggedIn).build())
    }
}
