package net.matsudamper.kmp.activitypub.graphql.resolver

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import graphql.execution.DataFetcherResult
import graphql.schema.DataFetchingEnvironment
import net.matsudamper.kmp.activitypub.graphql.GraphQlClientException
import net.matsudamper.kmp.activitypub.graphql.GraphQlContext
import net.matsudamper.kmp.activitypub.graphql.GraphQlEngine
import net.matsudamper.kmp.activitypub.graphql.model.QlDeletePasskeyFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlDeletePasskeyResult
import net.matsudamper.kmp.activitypub.graphql.model.QlPasskeyLoginOptions
import net.matsudamper.kmp.activitypub.graphql.model.QlPasskeyRegistrationOptions
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisterPasskeyFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisterPasskeyQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlRegisterPasskeyResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.QlUserMutation
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPasskeyLoginFailure
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPasskeyLoginQuery
import net.matsudamper.kmp.activitypub.graphql.model.QlUserPasskeyLoginResult
import net.matsudamper.kmp.activitypub.graphql.model.UserMutationResolver
import net.matsudamper.kmp.activitypub.logic.PasskeyService

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
        val accountService = GraphQlEngine.diContainer(env).accountService

        val accountId = accountService.authenticate(username = query.username, password = query.password)
        val result =
            if (accountId == null) {
                QlUserLoginResult(failure = QlUserLoginFailure.WRONG_USERNAME_OR_PASSWORD)
            } else {
                context.issueUserSession(accountId)
                QlUserLoginResult(failure = null)
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(result).build())
    }

    override fun passkeyLoginOptions(
        userMutation: QlUserMutation,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlPasskeyLoginOptions>> {
        val context = GraphQlEngine.graphQlContext(env)
        claimPasskeyChallenge(context)
        val options = passkeyService(env).loginOptions()

        return CompletableFuture.completedFuture(
            DataFetcherResult.Builder(QlPasskeyLoginOptions(challenge = options.challenge, rpId = options.rpId)).build(),
        )
    }

    override fun passkeyLogin(
        userMutation: QlUserMutation,
        query: QlUserPasskeyLoginQuery,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlUserPasskeyLoginResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        if (!context.claimLoginAttempt()) throw GraphQlClientException("login は 1 リクエストにつき 1 回まで")

        val accountId =
            passkeyService(env).login(
                credentialId = query.credentialId,
                clientDataJson = query.clientDataJson,
                authenticatorData = query.authenticatorData,
                signature = query.signature,
                userHandle = query.userHandle,
            )
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
    ): CompletionStage<DataFetcherResult<QlPasskeyRegistrationOptions?>> {
        val context = GraphQlEngine.graphQlContext(env)
        val passkeyService = passkeyService(env)
        val account =
            context.userAccountId()?.let { GraphQlEngine.diContainer(env).accountService.account(it) }
                ?: return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlPasskeyRegistrationOptions?>(null).build())
        claimPasskeyChallenge(context)

        val options = passkeyService.registrationOptions(account)
        val result =
            QlPasskeyRegistrationOptions(
                challenge = options.challenge,
                rpId = options.rpId,
                userHandle = options.userHandle,
                username = options.username,
                algorithms = options.algorithms.map { it.toInt() },
                excludeCredentialIds = options.excludeCredentialIds,
            )

        return CompletableFuture.completedFuture(DataFetcherResult.Builder<QlPasskeyRegistrationOptions?>(result).build())
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
                when (passkeyService.register(accountId = accountId, name = query.name, clientDataJson = query.clientDataJson, attestationObject = query.attestationObject)) {
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
        id: String,
        env: DataFetchingEnvironment,
    ): CompletionStage<DataFetcherResult<QlDeletePasskeyResult>> {
        val context = GraphQlEngine.graphQlContext(env)
        val passkeyService = passkeyService(env)

        val accountId = context.userAccountId()
        val failure =
            when {
                accountId == null -> QlDeletePasskeyFailure.NOT_LOGGED_IN
                passkeyService.delete(accountId = accountId, credentialId = id) -> null
                else -> QlDeletePasskeyFailure.NOT_FOUND
            }

        return CompletableFuture.completedFuture(DataFetcherResult.Builder(QlDeletePasskeyResult(failure = failure)).build())
    }

    private fun passkeyService(env: DataFetchingEnvironment): PasskeyService = GraphQlEngine.diContainer(env).passkeyService

    private fun claimPasskeyChallenge(context: GraphQlContext) {
        if (!context.claimPasskeyChallenge()) throw GraphQlClientException("パスキーの challenge は 1 リクエストにつき 1 回まで")
    }
}
