package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlContext
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserMutation
import net.matsudamper.kmp.activitypub.graphql.model.UserMutationResolver

class UserMutationResolverImpl : UserMutationResolver {
    /**
     * 照合はまだ無い。どの入力でも成功にしてダミーのセッション Cookie を付ける
     */
    override fun login(
        userMutation: QlUserMutation,
        query: QlUserLoginQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserLoginResult>> {
        GraphQlContext.from(env).requestDummySessionCookie()
        val result = QlUserLoginResult(failure = null)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }
}
