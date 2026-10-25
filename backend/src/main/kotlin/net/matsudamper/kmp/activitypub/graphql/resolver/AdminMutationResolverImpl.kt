package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlContext
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.AdminMutationResolver
import net.matsudamper.kmp.activitypub.graphql.model.QlAddUserQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminAddUserFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminAddUserResult
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminMutation
import net.matsudamper.kmp.activitypub.graphql.model.QlAdminSession
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.AdminLoginService
import net.matsudamper.kotpub.actor.ActorUsernameUtil

class AdminMutationResolverImpl : AdminMutationResolver {
    override fun login(
        adminMutation: QlAdminMutation,
        password: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminLoginResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        if (!context.claimLoginAttempt()) throw GraphQlClientException("login は 1 リクエストにつき 1 回まで")
        val diContainer = GraphQlEngine.diContainer(env)
        val adminLoginService = diContainer.adminLoginService
        val loginThrottle = diContainer.loginThrottle

        val result = when {
            adminLoginService.adminPasswordConfigured.not() -> {
                loginFailure(context = context, failure = QlAdminLoginFailure.NOT_CONFIGURED, adminLoginService = adminLoginService)
            }

            // 照合の前に数える。リクエストを並べて送られても、締め出した後はハッシュを計算しない
            loginThrottle.tryReserve(clientIp = context.clientIp, username = ADMIN_THROTTLE_USERNAME).not() -> {
                loginFailure(context = context, failure = QlAdminLoginFailure.WRONG_PASSWORD, adminLoginService = adminLoginService)
            }

            adminLoginService.matchesAdminPassword(password).not() -> {
                loginFailure(context = context, failure = QlAdminLoginFailure.WRONG_PASSWORD, adminLoginService = adminLoginService)
            }

            else -> {
                loginThrottle.recordSuccess(clientIp = context.clientIp, username = ADMIN_THROTTLE_USERNAME)
                context.issueAdminSession()
                QlAdminLoginResult(
                    session = QlAdminSession(loggedIn = true, passwordConfigured = true),
                    failure = null,
                )
            }
        }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun logout(
        adminMutation: QlAdminMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminSession>> {
        val context = GraphQlEngine.graphQlContext(env)
        context.clearAdminSession()
        val adminLoginService = GraphQlEngine.diContainer(env).adminLoginService

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(
                QlAdminSession(
                    loggedIn = false,
                    passwordConfigured = adminLoginService.adminPasswordConfigured,
                ),
            ).build(),
        )
    }

    override fun addUser(
        adminMutation: QlAdminMutation,
        query: QlAddUserQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlAdminAddUserResult>> {
        if (GraphQlEngine.graphQlContext(env).isAdminLoggedIn().not()) throw GraphQlClientException("管理画面にログインしていない")

        val accountService = GraphQlEngine.diContainer(env).accountService
        val result = when (val added = accountService.add(username = query.username, password = query.password)) {
            is AccountService.AddAccountResult.Success -> {
                QlAdminAddUserResult(adminAccount = added.account.toAdminGraphqlResponse(GraphQlEngine.diContainer(env).localDomain), failure = null)
            }

            is AccountService.AddAccountResult.Failure -> {
                QlAdminAddUserResult(adminAccount = null, failure = added.toGraphqlResponse())
            }
        }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    private fun AccountService.AddAccountResult.Failure.toGraphqlResponse(): QlAdminAddUserFailure =
        QlAdminAddUserFailure(
            unusableCharacters = unusableCharacters.map { it.toString() }.takeIf { it.isNotEmpty() },
            maxLength = ActorUsernameUtil.MAX_LENGTH.takeIf { tooLong },
            minLength = ActorUsernameUtil.MIN_LENGTH.takeIf { tooShort },
            isDuplicated = duplicated,
            passwordMinLength = AccountService.PASSWORD_MIN_LENGTH.takeIf { passwordTooShort },
        )

    private fun loginFailure(
        context: GraphQlContext,
        failure: QlAdminLoginFailure,
        adminLoginService: AdminLoginService,
    ): QlAdminLoginResult {
        return QlAdminLoginResult(
            session = QlAdminSession(
                loggedIn = context.isAdminLoggedIn(),
                passwordConfigured = adminLoginService.adminPasswordConfigured,
            ),
            failure = failure,
        )
    }

    private companion object {
        /**
         * ユーザー名に使えない文字を入れて、同じ名前のユーザーの締め出しと混ざらないようにする
         */
        const val ADMIN_THROTTLE_USERNAME = ":admin"
    }
}
