package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisteredPasskey
import net.matsudamper.kmp.activitypub.graphql.model.QlUser
import net.matsudamper.kmp.activitypub.graphql.model.QlUserInfo
import net.matsudamper.kmp.activitypub.graphql.model.QlUserSearchResult
import net.matsudamper.kmp.activitypub.graphql.model.QueryResolver
import net.matsudamper.kmp.activitypub.logic.AccountSearchCursor

class QueryResolverImpl : QueryResolver {
    override fun unused(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<Boolean?>> {
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<Boolean?>(null).build())
    }

    override fun userInfo(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<QlUserInfo?>> {
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        val userInfo =
            if (accountId == null) {
                null
            } else {
                QlUserInfo(id = accountId)
            }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlUserInfo?>(userInfo).build())
    }

    override fun passkeys(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<List<QlRegisteredPasskey>?>> {
        val passkeyService = GraphQlEngine.diContainer(env).passkeyService
        val passkeys =
            GraphQlEngine.graphQlContext(env).userAccountId()?.let { accountId ->
                passkeyService.passkeys(accountId).map { QlRegisteredPasskey(id = it.credentialId, name = it.name) }
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder<List<QlRegisteredPasskey>?>(passkeys).build())
    }

    override fun hasPassword(env: DataFetchingEnvironment): CompletionStage<DataFetcherResult<Boolean?>> {
        val accountService = GraphQlEngine.diContainer(env).accountService
        val hasPassword = GraphQlEngine.graphQlContext(env).userAccountId()?.let { accountService.hasPassword(it) }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder<Boolean?>(hasPassword).build())
    }

    override fun user(
        username: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUser?>> {
        val account = GraphQlEngine.diContainer(env).accountService.account(username = username)
        val user =
            if (account == null) {
                null
            } else {
                QlUser(username = account.username)
            }
        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlUser?>(user).build())
    }

    override fun searchUsers(
        query: String,
        after: String?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserSearchResult>> {
        val result =
            GraphQlEngine.diContainer(env).accountService.searchAccounts(
                keyword = query,
                cursor = after?.let { AccountSearchCursor(lastUsername = it) },
            )
        val userSearchResult =
            QlUserSearchResult(
                users = result.accounts.map { QlUser(username = it.username) },
                nextCursor = result.nextCursor?.lastUsername,
            )
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(userSearchResult).build())
    }
}
