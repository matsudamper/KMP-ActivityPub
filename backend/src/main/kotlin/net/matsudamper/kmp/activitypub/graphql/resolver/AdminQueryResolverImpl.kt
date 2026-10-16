package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.GraphqlExceptions
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.data.AccountsCursor
import net.matsudamper.kmp.activitypub.graphql.model.AdminQueryResolver
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminAccountsConnection
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminSession
import net.matsudamper.kmp.activitypub.graphql.model.QlPageInfo

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
        cursor: String?,
        limit: Int?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminAccountsConnection>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphqlExceptions.Admin()

        val after = cursor?.let { AccountsCursor.decode(it) }

        // 読めないカーソルは一覧の終わりとして扱う。外から来る値なので投げない
        val connection = if (cursor != null && after == null) {
            QlAdminAccountsConnection(
                nodes = listOf(),
                pageInfo = QlPageInfo(hasMore = false, nextCursor = null),
            )
        } else {
            val page = GraphQlEngine.diContainer(env).accountService.accounts(
                after = after?.toPosition(),
                limit = (limit ?: DEFAULT_ACCOUNTS_LIMIT).coerceIn(0, MAX_ACCOUNTS_LIMIT),
            )

            QlAdminAccountsConnection(
                nodes = page.accounts.map { it.toAdminGraphqlResponse() },
                pageInfo = QlPageInfo(
                    hasMore = page.hasMore,
                    nextCursor = page.nextPosition?.let { AccountsCursor.of(it).encode() },
                ),
            )
        }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(connection).build())
    }

    private companion object {
        const val DEFAULT_ACCOUNTS_LIMIT = 10
        const val MAX_ACCOUNTS_LIMIT = 50
    }
}
