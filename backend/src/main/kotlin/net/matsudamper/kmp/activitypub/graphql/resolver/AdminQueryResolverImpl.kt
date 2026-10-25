package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.AdminQueryResolver
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminAccountsConnection
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminSession

class AdminQueryResolverImpl : AdminQueryResolver {
    override fun session(
        adminQuery: QlAdminQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminSession>> {
        val context = GraphQlEngine.graphQlContext(env)
        val adminLoginService = GraphQlEngine.diContainer(env).adminLoginService

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(
                QlAdminSession(
                    loggedIn = context.isAdminLoggedIn(),
                    passwordConfigured = adminLoginService.adminPasswordConfigured,
                ),
            ).build(),
        )
    }

    override fun adminAccounts(
        adminQuery: QlAdminQuery,
        after: String?,
        limit: Int?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminAccountsConnection>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphQlClientException("管理画面にログインしていない")

        val diContainer = GraphQlEngine.diContainer(env)
        val page = diContainer.accountService.accounts(
            afterUsername = after,
            limit = (limit ?: DEFAULT_ACCOUNTS_LIMIT).coerceIn(0, MAX_ACCOUNTS_LIMIT),
        )
        val connection = QlAdminAccountsConnection(
            nodes = page.accounts.map { it.toAdminGraphqlResponse(diContainer.localDomain) },
            nextCursor = page.nextCursor,
        )

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(connection).build())
    }

    private companion object {
        const val DEFAULT_ACCOUNTS_LIMIT = 10
        const val MAX_ACCOUNTS_LIMIT = 50
    }
}
