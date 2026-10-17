package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserMutation
import net.matsudamper.kmp.activitypub.graphql.model.UserMutationResolver
import org.slf4j.LoggerFactory

class UserMutationResolverImpl : UserMutationResolver {
    /**
     * 通るとセッションの Cookie を返す
     */
    override fun login(
        userMutation: QlUserMutation,
        query: QlUserLoginQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserLoginResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        if (!context.claimLoginAttempt()) throw GraphQlClientException("login は 1 リクエストにつき 1 回まで")
        val diContainer = GraphQlEngine.diContainer(env)
        val loginThrottle = diContainer.loginThrottle
        val clientIp = context.clientIp
        if (loginThrottle.isLocked(clientIp)) {
            loginThrottle.recordFailure(clientIp)
            logger.warn("ログインの失敗が続いている IP なので照合せずに断り、締め出しを延ばした: $clientIp")
            val result = QlUserLoginResult(failure = QlUserLoginFailure.WRONG_USERNAME_OR_PASSWORD)
            return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
        }

        val accountId = diContainer.accountService.authenticate(username = query.username, password = query.password)
        val result =
            if (accountId == null) {
                loginThrottle.recordFailure(clientIp)
                QlUserLoginResult(failure = QlUserLoginFailure.WRONG_USERNAME_OR_PASSWORD)
            } else {
                loginThrottle.recordSuccess(clientIp)
                context.issueUserSession(accountId)
                QlUserLoginResult(failure = null)
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    private companion object {
        val logger = LoggerFactory.getLogger(UserMutationResolverImpl::class.java)
    }
}
