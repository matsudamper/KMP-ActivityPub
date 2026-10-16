package net.matsudamper.kmp.activitypub.frontend.logic.passkey

import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Optional
import net.matsudamper.kmp.activitypub.frontend.graphql.PasskeyLoginMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.PasskeyLoginOptionsMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.PasskeyRegistrationOptionsMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.RegisterPasskeyMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.type.RegisterPasskeyFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.RegisterPasskeyQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserPasskeyLoginFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserPasskeyLoginQuery
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource

class PasskeyApi(
    private val clients: GraphQlClientSource,
) {
    suspend fun loginOptions(): PasskeyOptionsResult<PasskeyAssertionRequest> {
        val client = clients.client.value ?: return PasskeyOptionsResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response = client.mutation(PasskeyLoginOptionsMutation()).execute()
        val options = response.data?.user?.passkeyLoginOptions
        if (response.exception != null || response.hasErrors() || options == null) return PasskeyOptionsResult.Failure(response.failureMessage())

        return PasskeyOptionsResult.Success(PasskeyAssertionRequest(challenge = options.challenge, rpId = options.rpId))
    }

    suspend fun login(assertion: PasskeyAssertion): PasskeyLoginResult {
        val client = clients.client.value ?: return PasskeyLoginResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response =
            client
                .mutation(
                    PasskeyLoginMutation(
                        UserPasskeyLoginQuery(
                            credentialId = assertion.credentialId,
                            clientDataJson = assertion.clientDataJson,
                            authenticatorData = assertion.authenticatorData,
                            signature = assertion.signature,
                            userHandle = Optional.presentIfNotNull(assertion.userHandle),
                        ),
                    ),
                ).execute()
        val login = response.data?.user?.passkeyLogin
        if (response.exception != null || response.hasErrors() || login == null) return PasskeyLoginResult.Failure(response.failureMessage())

        return when (login.failure) {
            null -> PasskeyLoginResult.Success
            UserPasskeyLoginFailure.NOT_ACCEPTED -> PasskeyLoginResult.NotAccepted
            UserPasskeyLoginFailure.UNKNOWN__ -> PasskeyLoginResult.Failure("Unknown")
        }
    }

    suspend fun registrationOptions(): PasskeyOptionsResult<PasskeyCreationRequest> {
        val client = clients.client.value ?: return PasskeyOptionsResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response = client.mutation(PasskeyRegistrationOptionsMutation()).execute()
        val user = response.data?.user
        if (response.exception != null || response.hasErrors() || user == null) return PasskeyOptionsResult.Failure(response.failureMessage())
        val options = user.passkeyRegistrationOptions ?: return PasskeyOptionsResult.NotLoggedIn

        return PasskeyOptionsResult.Success(
            PasskeyCreationRequest(
                challenge = options.challenge,
                rpId = options.rpId,
                userHandle = options.userHandle,
                username = options.username,
                algorithms = options.algorithms,
                excludeCredentialIds = options.excludeCredentialIds,
            ),
        )
    }

    suspend fun register(creation: PasskeyCreation): PasskeyRegistrationResult {
        val client = clients.client.value ?: return PasskeyRegistrationResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response =
            client
                .mutation(
                    RegisterPasskeyMutation(
                        RegisterPasskeyQuery(
                            clientDataJson = creation.clientDataJson,
                            attestationObject = creation.attestationObject,
                        ),
                    ),
                ).execute()
        val register = response.data?.user?.registerPasskey
        if (response.exception != null || response.hasErrors() || register == null) return PasskeyRegistrationResult.Failure(response.failureMessage())

        return when (register.failure) {
            null -> PasskeyRegistrationResult.Success
            RegisterPasskeyFailure.NOT_LOGGED_IN -> PasskeyRegistrationResult.NotLoggedIn
            RegisterPasskeyFailure.NOT_ACCEPTED -> PasskeyRegistrationResult.NotAccepted
            RegisterPasskeyFailure.ALREADY_REGISTERED -> PasskeyRegistrationResult.AlreadyRegistered
            RegisterPasskeyFailure.UNKNOWN__ -> PasskeyRegistrationResult.Failure("Unknown")
        }
    }

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }

    private companion object {
        const val NOT_CONFIGURED_MESSAGE = "接続先が未設定"
    }
}
