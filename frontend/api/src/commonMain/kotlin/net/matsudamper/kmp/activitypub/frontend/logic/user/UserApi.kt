package net.matsudamper.kmp.activitypub.frontend.logic.user

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.watch
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLoginMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLogoutMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserPostNoteMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserSessionQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.UserSessionFields
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserLoginFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserLoginQuery

class UserApi(
    private val client: ApolloClient,
) {
    fun session(): Flow<UserSessionResult> {
        return client
            .query(UserSessionQuery())
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .watch()
            .map { response ->
                val data = response.data ?: return@map UserSessionResult.Failure(response.failureMessage())
                data.user.session.userSessionFields.toSessionResult()
            }
    }

    suspend fun login(
        username: String,
        password: String,
    ): UserLoginResult {
        val response = client
            .mutation(UserLoginMutation(UserLoginQuery(username = username, password = password)))
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .execute()
        val login = response.data?.user?.login ?: return UserLoginResult.Failure(response.failureMessage())

        return when (login.failure) {
            null -> {
                // 画面が見ているのはセッションの問い合わせなので、ログインしたことをそちらに流す
                client.query(UserSessionQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()
                UserLoginResult.Success
            }

            UserLoginFailure.WRONG_USERNAME_OR_PASSWORD -> UserLoginResult.WrongUsernameOrPassword

            UserLoginFailure.UNKNOWN__ -> UserLoginResult.Failure("Unknown")
        }
    }

    suspend fun logout(): UserSessionResult {
        val response = client
            .mutation(UserLogoutMutation())
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .execute()
        val data = response.data ?: return UserSessionResult.Failure(response.failureMessage())
        client.query(UserSessionQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()
        return data.user.logout.userSessionFields.toSessionResult()
    }

    suspend fun postNote(body: String): UserPostNoteResult {
        val response = client
            .mutation(UserPostNoteMutation(body))
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .execute()
        val posted = response.data?.user?.postNote ?: return UserPostNoteResult.Failure(response.failureMessage())

        val failure = posted.failure
        return when {
            failure != null -> UserPostNoteResult.Rejected(isEmpty = failure.isEmpty, maxLength = failure.maxLength)
            posted.note != null -> UserPostNoteResult.Success
            else -> UserPostNoteResult.Failure("投稿できたが内容が返ってこない")
        }
    }

    private fun UserSessionFields.toSessionResult(): UserSessionResult {
        val account = account ?: return UserSessionResult.LoggedOut
        return UserSessionResult.LoggedIn(username = account.username, acct = account.acct)
    }

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }
}
