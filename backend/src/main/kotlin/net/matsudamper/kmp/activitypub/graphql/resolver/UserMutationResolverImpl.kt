package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserMutation
import net.matsudamper.kmp.activitypub.graphql.model.QlUserSession
import net.matsudamper.kmp.activitypub.graphql.model.UserMutationResolver

/**
 * 照合はまだ無い。どの入力でも失敗にする
 */
class UserMutationResolverImpl : UserMutationResolver {
    override fun login(
        userMutation: QlUserMutation,
        query: QlUserLoginQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserLoginResult>> {
        val result =
            QlUserLoginResult(
                session = QlUserSession(account = null),
                failure = QlUserLoginFailure.WRONG_USERNAME_OR_PASSWORD,
            )
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }
}
