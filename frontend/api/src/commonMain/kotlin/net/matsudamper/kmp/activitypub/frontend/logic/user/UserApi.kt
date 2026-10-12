package net.matsudamper.kmp.activitypub.frontend.logic.user

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLoginMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserLoginFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserLoginQuery

class UserApi(
    private val client: ApolloClient,
) {
    suspend fun login(
        username: String,
        password: String,
    ): UserLoginResult {
        val response = client
            .mutation(UserLoginMutation(UserLoginQuery(username = username, password = password)))
            .execute()
        if (response.exception != null || response.hasErrors()) return UserLoginResult.Failure(response.failureMessage())
        val login = response.data?.user?.login ?: return UserLoginResult.Failure(response.failureMessage())

        return when (login.failure) {
            null -> UserLoginResult.Success
            UserLoginFailure.WRONG_USERNAME_OR_PASSWORD -> UserLoginResult.WrongUsernameOrPassword
            UserLoginFailure.UNKNOWN__ -> UserLoginResult.Failure("Unknown")
        }
    }

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }
}
