package net.matsudamper.kmp.activitypub.frontend.logic.admin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminAccountsScreenQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminAddUserMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminLoginMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminLogoutMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminSessionQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.AdminAccountListFields
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.AdminSessionFields
import net.matsudamper.kmp.activitypub.frontend.graphql.type.AddUserQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.type.AdminLoginFailure
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.frontend.logic.Paging
import net.matsudamper.kmp.activitypub.frontend.logic.PagingLoadMoreResult

private const val GRAPHQL_PATH = "/graphql"

class AdminApi(
    private val client: ApolloClient = GraphQlClient.builder(GRAPHQL_PATH).build(),
) {
    fun session(): Flow<AdminSessionResult> = flow {
        emit(client.query(AdminSessionQuery()).execute().toSessionResult { it.admin.session.adminSessionFields })
    }

    suspend fun login(password: String): AdminLoginResult {
        val response =
            client
                .mutation(AdminLoginMutation(password))
                .execute()
        val login = response.data?.admin?.login ?: return AdminLoginResult.Failure(response.failureMessage())

        return when (login.failure) {
            null -> AdminLoginResult.Success
            AdminLoginFailure.WRONG_PASSWORD -> AdminLoginResult.WrongPassword
            AdminLoginFailure.NOT_CONFIGURED -> AdminLoginResult.NotConfigured
            AdminLoginFailure.UNKNOWN__ -> AdminLoginResult.Failure("Unknown")
        }
    }

    suspend fun logout(): AdminSessionResult {
        return client
            .mutation(AdminLogoutMutation())
            .execute()
            .toSessionResult { it.admin.logout.adminSessionFields }
    }

    /**
     * @param limit 1 ページで要求する件数。上限はサーバー側で決まる
     */
    fun accounts(limit: Int): Paging<AdminAccountsResult> = AdminAccountsPaging(limit)

    /**
     * 先頭のページを読み、続きは読んだ分に足して同じ [watch] から流す
     */
    private inner class AdminAccountsPaging(
        private val limit: Int,
    ) : Paging<AdminAccountsResult> {
        private val pages = MutableStateFlow<AdminAccountsResult?>(null)

        override fun watch(): Flow<AdminAccountsResult> = flow {
            pages.value = fetchAccounts(after = null)
            emitAll(pages.filterNotNull())
        }

        override suspend fun loadMore(cursor: String): PagingLoadMoreResult {
            return when (val fetched = fetchAccounts(after = cursor)) {
                is AdminAccountsResult.Failure -> PagingLoadMoreResult.Failure(fetched.message)

                is AdminAccountsResult.Success -> {
                    pages.update { loaded ->
                        val loadedAccounts = (loaded as? AdminAccountsResult.Success)?.accounts.orEmpty()
                        fetched.copy(accounts = loadedAccounts + fetched.accounts)
                    }
                    PagingLoadMoreResult.Success
                }
            }
        }

        private suspend fun fetchAccounts(after: String?): AdminAccountsResult =
            client
                .query(AdminAccountsScreenQuery(after = Optional.presentIfNotNull(after), limit = Optional.present(limit)))
                .execute()
                .toAdminAccountsResult()
    }

    suspend fun addUser(
        username: String,
        password: String,
    ): AdminAddAccountResult {
        val response = client
            .mutation(AdminAddUserMutation(AddUserQuery(username = username, password = password)))
            .execute()
        val added = response.data?.admin?.addUser ?: return AdminAddAccountResult.Failure(response.failureMessage())

        val failure = added.failure
            ?: return AdminAddAccountResult.Success(
                added.adminAccount?.adminAccountListFields?.username
                    ?: return AdminAddAccountResult.Failure("登録できたが内容が返ってこない"),
            )

        return AdminAddAccountResult.Rejected(
            unusableCharacters = failure.unusableCharacters.orEmpty(),
            maxLength = failure.maxLength,
            minLength = failure.minLength,
            isDuplicated = failure.isDuplicated,
            passwordMinLength = failure.passwordMinLength,
        )
    }

    private fun ApolloResponse<AdminAccountsScreenQuery.Data>.toAdminAccountsResult(): AdminAccountsResult {
        if (exception != null || errors.orEmpty().isNotEmpty()) {
            return AdminAccountsResult.Failure(failureMessage())
        }

        val data = data ?: return AdminAccountsResult.Failure(failureMessage())

        return AdminAccountsResult.Success(
            accounts = data.admin.adminAccounts.nodes.map { it.adminAccountListFields.toAdminAccount() },
            nextCursor = data.admin.adminAccounts.nextCursor,
        )
    }

    private fun AdminAccountListFields.toAdminAccount(): AdminAccount = AdminAccount(
        username = username,
        acct = acct,
    )

    /**
     * `data` が無いのは失敗。ログインしていない状態と混ぜない
     */
    private fun <D : Operation.Data> ApolloResponse<D>.toSessionResult(
        select: (D) -> AdminSessionFields,
    ): AdminSessionResult {
        val data = data ?: return AdminSessionResult.Failure(failureMessage())
        val session = select(data)

        return AdminSessionResult.Success(
            loggedIn = session.loggedIn,
            passwordConfigured = session.passwordConfigured,
        )
    }

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }
}
