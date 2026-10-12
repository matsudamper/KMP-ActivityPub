package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlUserAccount
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserMutation
import net.matsudamper.kmp.activitypub.graphql.model.QlUserSession
import net.matsudamper.kmp.activitypub.graphql.model.UserMutationResolver

class UserMutationResolverImpl : UserMutationResolver {
    override fun login(
        userMutation: QlUserMutation,
        query: QlUserLoginQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserLoginResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        val accountService = GraphQlEngine.diContainer(env).accountService

        val accountId = accountService.authenticate(username = query.username, password = query.password)
        val account = accountId?.let { accountService.account(it) }

        val result = if (account == null) {
            QlUserLoginResult(
                session = QlUserSession(account = null),
                failure = QlUserLoginFailure.WRONG_USERNAME_OR_PASSWORD,
            )
        } else {
            context.issueUserSession(account.id)
            QlUserLoginResult(
                session = QlUserSession(account = QlUserAccount(username = account.username)),
                failure = null,
            )
        }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }
}
