package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlUserQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserSession
import net.matsudamper.kmp.activitypub.graphql.model.UserQueryResolver

class UserQueryResolverImpl : UserQueryResolver {
    override fun session(
        userQuery: QlUserQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserSession>> {
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        val account = accountId?.let { GraphQlEngine.diContainer(env).accountService.account(it) }

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(QlUserSession(account = account?.toUserGraphqlResponse())).build(),
        )
    }
}
