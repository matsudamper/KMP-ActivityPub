package net.matsudamper.kmp.activitypub.frontend.logic.passkey

import com.apollographql.apollo.api.ApolloResponse
import net.matsudamper.kmp.activitypub.frontend.graphql.DeletePasskeyMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.DeletePasswordMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.PasskeyLoginMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.PasskeyLoginOptionsMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.PasskeyRegistrationOptionsMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.RegisterPasskeyMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.SettingsScreenPasskeysQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.type.DeletePasskeyFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.DeletePasswordFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.RegisterPasskeyFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.RegisterPasskeyQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserPasskeyLoginFailure
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource

class PasskeyApi(
    private val clients: GraphQlClientSource,
) {
    suspend fun loginOptions(): PasskeyOptionsResult<PasskeyAuthenticator.AssertionRequest> {
        val client = clients.client.value ?: return PasskeyOptionsResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response = client.mutation(PasskeyLoginOptionsMutation()).execute()
        val options = response.data?.user?.passkeyLoginOptions
        if (response.exception != null || response.hasErrors() || options == null) return PasskeyOptionsResult.Failure(response.failureMessage())

        return PasskeyOptionsResult.Success(PasskeyAuthenticator.AssertionRequest(optionsJson = options))
    }

    suspend fun login(assertion: PasskeyAuthenticator.Assertion): PasskeyLoginResult {
        val client = clients.client.value ?: return PasskeyLoginResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response =
            client
                .mutation(PasskeyLoginMutation(credential = assertion.credentialJson))
                .execute()
        val login = response.data?.user?.passkeyLogin
        if (response.exception != null || response.hasErrors() || login == null) return PasskeyLoginResult.Failure(response.failureMessage())

        return when (login.failure) {
            null -> PasskeyLoginResult.Success
            UserPasskeyLoginFailure.NOT_ACCEPTED -> PasskeyLoginResult.NotAccepted
            UserPasskeyLoginFailure.UNKNOWN__ -> PasskeyLoginResult.Failure("Unknown")
        }
    }

    suspend fun registrationOptions(): PasskeyOptionsResult<PasskeyAuthenticator.CreationRequest> {
        val client = clients.client.value ?: return PasskeyOptionsResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response = client.mutation(PasskeyRegistrationOptionsMutation()).execute()
        val user = response.data?.user
        if (response.exception != null || response.hasErrors() || user == null) return PasskeyOptionsResult.Failure(response.failureMessage())
        val options = user.passkeyRegistrationOptions ?: return PasskeyOptionsResult.NotLoggedIn

        return PasskeyOptionsResult.Success(PasskeyAuthenticator.CreationRequest(optionsJson = options))
    }

    suspend fun register(
        name: String,
        creation: PasskeyAuthenticator.Creation,
    ): PasskeyRegistrationResult {
        val client = clients.client.value ?: return PasskeyRegistrationResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response =
            client
                .mutation(
                    RegisterPasskeyMutation(
                        RegisterPasskeyQuery(
                            name = name,
                            credential = creation.credentialJson,
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
            RegisterPasskeyFailure.INVALID_NAME -> PasskeyRegistrationResult.InvalidName
            RegisterPasskeyFailure.UNKNOWN__ -> PasskeyRegistrationResult.Failure("Unknown")
        }
    }

    suspend fun passkeys(): PasskeyListResult {
        val client = clients.client.value ?: return PasskeyListResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response = client.query(SettingsScreenPasskeysQuery()).execute()
        val data = response.data
        if (response.exception != null || response.hasErrors() || data == null) return PasskeyListResult.Failure(response.failureMessage())
        val passkeys = data.passkeys ?: return PasskeyListResult.NotLoggedIn
        val hasPassword = data.hasPassword ?: return PasskeyListResult.NotLoggedIn

        return PasskeyListResult.Success(
            passkeys = passkeys.map { RegisteredPasskey(id = it.id, name = it.name) },
            hasPassword = hasPassword,
        )
    }

    suspend fun delete(id: String): PasskeyDeleteResult {
        val client = clients.client.value ?: return PasskeyDeleteResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response = client.mutation(DeletePasskeyMutation(id = id)).execute()
        val delete = response.data?.user?.deletePasskey
        if (response.exception != null || response.hasErrors() || delete == null) return PasskeyDeleteResult.Failure(response.failureMessage())

        return when (delete.failure) {
            null -> PasskeyDeleteResult.Success
            DeletePasskeyFailure.NOT_LOGGED_IN -> PasskeyDeleteResult.NotLoggedIn
            DeletePasskeyFailure.NOT_FOUND -> PasskeyDeleteResult.NotFound
            DeletePasskeyFailure.LAST_LOGIN_METHOD -> PasskeyDeleteResult.LastLoginMethod
            DeletePasskeyFailure.UNKNOWN__ -> PasskeyDeleteResult.Failure("Unknown")
        }
    }

    suspend fun deletePassword(): PasswordDeleteResult {
        val client = clients.client.value ?: return PasswordDeleteResult.Failure(NOT_CONFIGURED_MESSAGE)
        val response = client.mutation(DeletePasswordMutation()).execute()
        val delete = response.data?.user?.deletePassword
        if (response.exception != null || response.hasErrors() || delete == null) return PasswordDeleteResult.Failure(response.failureMessage())

        return when (delete.failure) {
            null -> PasswordDeleteResult.Success
            DeletePasswordFailure.NOT_LOGGED_IN -> PasswordDeleteResult.NotLoggedIn
            DeletePasswordFailure.NO_PASSKEY -> PasswordDeleteResult.NoPasskey
            DeletePasswordFailure.UNKNOWN__ -> PasswordDeleteResult.Failure("Unknown")
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
