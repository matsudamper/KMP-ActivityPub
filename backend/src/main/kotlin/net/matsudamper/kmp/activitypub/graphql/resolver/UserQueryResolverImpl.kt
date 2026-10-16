package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlMe
import net.matsudamper.kmp.activitypub.graphql.model.QlUserQuery
import net.matsudamper.kmp.activitypub.graphql.model.UserQueryResolver

class UserQueryResolverImpl : UserQueryResolver {
    override fun me(
        userQuery: QlUserQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlMe?>> {
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        val me =
            if (accountId == null) {
                null
            } else {
                QlMe(id = accountId.value.toString())
            }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlMe?>(me).build())
    }
}
