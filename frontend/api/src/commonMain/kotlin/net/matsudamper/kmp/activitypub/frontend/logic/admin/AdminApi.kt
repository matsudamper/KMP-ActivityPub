package net.matsudamper.kmp.activitypub.frontend.logic.admin

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Optional
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.watch
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminAccountsScreenQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminAddUserMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminLoginMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminLogoutMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminSessionQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.AdminAccountListFields
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.AdminSessionFields
import net.matsudamper.kmp.activitypub.frontend.graphql.type.AddUserQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.type.AdminLoginFailure
import net.matsudamper.kmp.activitypub.frontend.logic.CachedPaging
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClient
import net.matsudamper.kmp.activitypub.frontend.logic.Paging

class AdminApi(
    private val client: ApolloClient = GraphQlClient.apollo,
) {
    fun session(): Flow<AdminSessionResult> {
        return client
            .query(AdminSessionQuery())
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .watch()
            .map { response -> response.toSessionResult { it.admin.session.adminSessionFields } }
    }

    suspend fun login(password: String): AdminLoginResult {
        val response =
            client
                .mutation(AdminLoginMutation(password))
                .fetchPolicy(FetchPolicy.NetworkOnly)
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
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .execute()
            .toSessionResult { it.admin.logout.adminSessionFields }
    }

    /**
     * @param limit 1 ページで要求する件数。上限はサーバー側で決まる
     */
    fun accounts(limit: Int): Paging<AdminAccountsResult> {
        return CachedPaging(
            client = client,
            firstPage = AdminAccountsScreenQuery(
                cursor = Optional.absent(),
                limit = Optional.present(limit),
            ),
            nextPage = { cursor ->
                AdminAccountsScreenQuery(
                    cursor = Optional.present(cursor),
                    limit = Optional.present(limit),
                )
            },
            appendPage = { cached, fetched ->
                cached.copy(
                    admin = cached.admin.copy(
                        adminAccounts = cached.admin.adminAccounts.copy(
                            nodes = cached.admin.adminAccounts.nodes + fetched.admin.adminAccounts.nodes,
                            pageInfo = fetched.admin.adminAccounts.pageInfo,
                        ),
                    ),
                )
            },
            toResult = { response -> response.toAdminAccountsResult() },
        )
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
            hasMore = data.admin.adminAccounts.pageInfo.hasMore,
            nextCursor = data.admin.adminAccounts.pageInfo.nextCursor,
        )
    }

    private fun AdminAccountListFields.toAdminAccount(): AdminAccount = AdminAccount(
        username = username,
        acct = acct,
        createdAt = createdAt,
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
