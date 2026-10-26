package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import kotlin.io.encoding.Base64
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlContext
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlDeletePasskeyFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlDeletePasskeyResult
import net.matsudamper.kmp.activitypub.graphql.model.QlDeletePasswordFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlDeletePasswordResult
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisterPasskeyFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisterPasskeyQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisterPasskeyResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUpdateProfileFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUpdateProfileResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserMutation
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPasskeyLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPasskeyLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPostNoteFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPostNoteResult
import net.matsudamper.kmp.activitypub.graphql.model.UserMutationResolver
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kmp.activitypub.logic.PasskeyService
import net.matsudamper.kmp.activitypub.logic.PostNoteResult
import net.matsudamper.kmp.activitypub.logic.UpdateProfileResult
import net.matsudamper.kmp.activitypub.repository.PasskeyDeleteResult
import net.matsudamper.kmp.activitypub.shared.PasskeyCredentialId
import net.matsudamper.kmp.activitypub.telemetry.TraceLogger

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
        val username = query.username.trim()
        if (!loginThrottle.tryReserve(clientIp = clientIp, username = username)) {
            val traceLogger = TraceLogger.impl()
            traceLogger.setAttribute("login.client_ip", clientIp)
            traceLogger.setAttribute("login.username", username)
            traceLogger.noticeInfo("ログインの失敗が続いている IP なので照合せずに断り、締め出しを延ばした")
            val result = QlUserLoginResult(failure = QlUserLoginFailure.WRONG_USERNAME_OR_PASSWORD)
            return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
        }

        val accountId = diContainer.accountService.authenticate(username = username, password = query.password)
        val result =
            if (accountId == null) {
                QlUserLoginResult(failure = QlUserLoginFailure.WRONG_USERNAME_OR_PASSWORD)
            } else {
                loginThrottle.recordSuccess(clientIp = clientIp, username = username)
                context.issueUserSession(accountId)
                QlUserLoginResult(failure = null)
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun passkeyLoginOptions(
        userMutation: QlUserMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<String>> {
        val context = GraphQlEngine.graphQlContext(env)
        claimPasskeyChallenge(context)

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(passkeyService(env).loginOptions()).build())
    }

    override fun passkeyLogin(
        userMutation: QlUserMutation,
        credential: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserPasskeyLoginResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        if (!context.claimLoginAttempt()) throw GraphQlClientException("login は 1 リクエストにつき 1 回まで")

        val accountId = passkeyService(env).login(credentialJson = credential)
        val result =
            if (accountId == null) {
                QlUserPasskeyLoginResult(failure = QlUserPasskeyLoginFailure.NOT_ACCEPTED)
            } else {
                context.issueUserSession(accountId)
                QlUserPasskeyLoginResult(failure = null)
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun passkeyRegistrationOptions(
        userMutation: QlUserMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<String?>> {
        val context = GraphQlEngine.graphQlContext(env)
        val account =
            context.userAccountId()?.let { GraphQlEngine.diContainer(env).accountService.account(it) }
                ?: return CompletableFuture.completedFuture(DataFetcherResult.Builder<String?>(null).build())
        claimPasskeyChallenge(context)

        return CompletableFuture.completedFuture(DataFetcherResult.Builder<String?>(passkeyService(env).registrationOptions(account)).build())
    }

    override fun registerPasskey(
        userMutation: QlUserMutation,
        query: QlRegisterPasskeyQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlRegisterPasskeyResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        val passkeyService = passkeyService(env)

        val accountId = context.userAccountId()
        val failure =
            if (accountId == null) {
                QlRegisterPasskeyFailure.NOT_LOGGED_IN
            } else {
                when (passkeyService.register(accountId = accountId, name = query.name, credentialJson = query.credential)) {
                    PasskeyService.RegistrationResult.Success -> null
                    PasskeyService.RegistrationResult.NotAccepted -> QlRegisterPasskeyFailure.NOT_ACCEPTED
                    PasskeyService.RegistrationResult.AlreadyRegistered -> QlRegisterPasskeyFailure.ALREADY_REGISTERED
                    PasskeyService.RegistrationResult.InvalidName -> QlRegisterPasskeyFailure.INVALID_NAME
                }
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlRegisterPasskeyResult(failure = failure)).build())
    }

    override fun deletePasskey(
        userMutation: QlUserMutation,
        id: PasskeyCredentialId,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlDeletePasskeyResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        val passkeyService = passkeyService(env)

        val accountId = context.userAccountId()
        val failure =
            if (accountId == null) {
                QlDeletePasskeyFailure.NOT_LOGGED_IN
            } else {
                when (passkeyService.delete(accountId = accountId, credentialId = id)) {
                    PasskeyDeleteResult.Deleted -> null
                    PasskeyDeleteResult.NotFound -> QlDeletePasskeyFailure.NOT_FOUND
                    PasskeyDeleteResult.LastLoginMethod -> QlDeletePasskeyFailure.LAST_LOGIN_METHOD
                }
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlDeletePasskeyResult(failure = failure)).build())
    }

    override fun deletePassword(
        userMutation: QlUserMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlDeletePasswordResult>> {
        if (GraphQlEngine.diContainer(env).passkeyService == null) throw GraphQlClientException("パスキーが無効なのでパスワードを消せない")
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId()
        val failure =
            if (accountId == null) {
                QlDeletePasswordFailure.NOT_LOGGED_IN
            } else {
                when (GraphQlEngine.diContainer(env).accountService.deletePassword(accountId)) {
                    AccountService.DeletePasswordResult.Success -> null
                    AccountService.DeletePasswordResult.NoPasskey -> QlDeletePasswordFailure.NO_PASSKEY
                }
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlDeletePasswordResult(failure = failure)).build())
    }

    override fun logout(
        userMutation: QlUserMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<Boolean>> {
        GraphQlEngine.graphQlContext(env).revokeUserSession()
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(true).build())
    }

    override fun postNote(
        userMutation: QlUserMutation,
        body: String,
        requestId: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserPostNoteResult>> {
        val result = postNote(body = body, requestId = requestId, env = env)
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun updateProfile(
        userMutation: QlUserMutation,
        displayName: String,
        icon: String?,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUpdateProfileResult>> {
        val result = QlUpdateProfileResult(failure = updateProfile(displayName = displayName, icon = icon, env = env))
        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    private fun passkeyService(env: DataFetchingEnvironment): PasskeyService =
        GraphQlEngine.diContainer(env).passkeyService ?: throw GraphQlClientException("パスキーは無効")

    private fun claimPasskeyChallenge(context: GraphQlContext) {
        if (!context.claimPasskeyChallenge()) throw GraphQlClientException("パスキーの challenge は 1 リクエストにつき 1 回まで")
    }

    private fun postNote(
        body: String,
        requestId: String,
        env: DataFetchingEnvironment,
    ): QlUserPostNoteResult {
        val diContainer = GraphQlEngine.diContainer(env)
        val accountId =
            GraphQlEngine.graphQlContext(env).userAccountId()
                ?: return QlUserPostNoteResult(url = null, failure = QlUserPostNoteFailure.NOT_LOGGED_IN)
        // セッションが残っていてもアカウントが消えていることがある
        val account =
            diContainer.accountService.account(accountId)
                ?: return QlUserPostNoteResult(url = null, failure = QlUserPostNoteFailure.NOT_LOGGED_IN)
        val notePostService =
            diContainer.notePostService
                ?: return QlUserPostNoteResult(url = null, failure = QlUserPostNoteFailure.FEDERATION_DISABLED)

        return when (val result = notePostService.post(username = account.username, body = body, requestId = requestId)) {
            is PostNoteResult.Posted -> QlUserPostNoteResult(url = result.url, failure = null)
            PostNoteResult.EmptyBody -> QlUserPostNoteResult(url = null, failure = QlUserPostNoteFailure.EMPTY_BODY)
            PostNoteResult.TooLong -> QlUserPostNoteResult(url = null, failure = QlUserPostNoteFailure.TOO_LONG)
            PostNoteResult.AccountNotFound -> QlUserPostNoteResult(url = null, failure = QlUserPostNoteFailure.NOT_LOGGED_IN)
        }
    }

    private fun updateProfile(
        displayName: String,
        icon: String?,
        env: DataFetchingEnvironment,
    ): QlUpdateProfileFailure? {
        val diContainer = GraphQlEngine.diContainer(env)
        val accountId = GraphQlEngine.graphQlContext(env).userAccountId() ?: return QlUpdateProfileFailure.NOT_LOGGED_IN
        // セッションが残っていてもアカウントが消えていることがある
        val account = diContainer.accountService.account(accountId) ?: return QlUpdateProfileFailure.NOT_LOGGED_IN
        val profileUpdateService = diContainer.profileUpdateService ?: return QlUpdateProfileFailure.FEDERATION_DISABLED
        val iconBytes =
            if (icon == null) {
                null
            } else {
                runCatching { Base64.Default.decode(icon) }.getOrNull() ?: return QlUpdateProfileFailure.ICON_UNSUPPORTED
            }

        return when (profileUpdateService.update(username = account.username, displayName = displayName, icon = iconBytes)) {
            UpdateProfileResult.Updated -> null
            UpdateProfileResult.TooLong -> QlUpdateProfileFailure.TOO_LONG
            UpdateProfileResult.IconTooLarge -> QlUpdateProfileFailure.ICON_TOO_LARGE
            UpdateProfileResult.IconDimensionsTooLarge -> QlUpdateProfileFailure.ICON_DIMENSIONS_TOO_LARGE
            UpdateProfileResult.IconUnsupported -> QlUpdateProfileFailure.ICON_UNSUPPORTED
            UpdateProfileResult.ImageStorageDisabled -> QlUpdateProfileFailure.IMAGE_STORAGE_DISABLED
            UpdateProfileResult.IconUploadFailed -> QlUpdateProfileFailure.ICON_UPLOAD_FAILED
            UpdateProfileResult.AccountNotFound -> QlUpdateProfileFailure.NOT_LOGGED_IN
        }
    }
}
