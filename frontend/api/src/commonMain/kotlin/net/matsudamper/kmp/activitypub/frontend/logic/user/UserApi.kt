package net.matsudamper.kmp.activitypub.frontend.logic.user

import kotlin.io.encoding.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Optional
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowerUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowingUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenResolveUserQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenSearchUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.LoginGateQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.MyProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.SetFollowingMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UpdateProfileIconMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UpdateProfileMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLoginMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLogoutMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserPostNoteMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.ActorListFields
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UpdateProfileFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UpdateProfileIconFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UpdateProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserLoginFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserLoginQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserPostNoteFailure
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource

class UserApi(
    private val clients: GraphQlClientSource,
) {
    private val followingChangesFlow = MutableSharedFlow<FollowingChange>()
    val followingChanges = followingChangesFlow.asSharedFlow()
    val ready: Flow<Boolean> = clients.client.map { it != null }.distinctUntilChanged()

    fun isReady(): Boolean = clients.client.value != null

    suspend fun setFollowing(actorUri: String, followed: Boolean): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(SetFollowingMutation(actorUri = actorUri, followed = followed)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        if (response.data?.user?.setFollowing != true) return "フォロー状態を変更できませんでした"
        followingChangesFlow.emit(FollowingChange(actorUri = actorUri, following = followed))
        return null
    }

    suspend fun followingUsers(after: String?): UserFollowListResult {
        val client = clients.awaitLoadedClient() ?: return UserFollowListResult.Failure("接続先が未設定")
        val response = client.query(FollowingUsersQuery(after = Optional.presentIfNotNull(after))).execute()
        if (response.exception != null || response.hasErrors()) return UserFollowListResult.Failure(response.failureMessage())
        val data = response.data ?: return UserFollowListResult.Failure(response.failureMessage())
        val actorList = data.followingUsers ?: return UserFollowListResult.NotLoggedIn

        return actorList.actorListFields.toUserFollowListResult()
    }

    suspend fun followerUsers(after: String?): UserFollowListResult {
        val client = clients.awaitLoadedClient() ?: return UserFollowListResult.Failure("接続先が未設定")
        val response = client.query(FollowerUsersQuery(after = Optional.presentIfNotNull(after))).execute()
        if (response.exception != null || response.hasErrors()) return UserFollowListResult.Failure(response.failureMessage())
        val data = response.data ?: return UserFollowListResult.Failure(response.failureMessage())
        val actorList = data.followerUsers ?: return UserFollowListResult.NotLoggedIn

        return actorList.actorListFields.toUserFollowListResult()
    }

    suspend fun login(
        username: String,
        password: String,
    ): UserLoginResult {
        val client = clients.client.value ?: return UserLoginResult.Failure("接続先が未設定")
        val response =
            client
                .mutation(
                    UserLoginMutation(
                        UserLoginQuery(
                            username = username,
                            password = password,
                        ),
                    ),
                ).execute()
        if (response.exception != null || response.hasErrors()) return UserLoginResult.Failure(response.failureMessage())
        val login = response.data?.user?.login ?: return UserLoginResult.Failure(response.failureMessage())

        return when (login.failure) {
            null -> UserLoginResult.Success
            UserLoginFailure.WRONG_USERNAME_OR_PASSWORD -> UserLoginResult.WrongUsernameOrPassword
            UserLoginFailure.UNKNOWN__ -> UserLoginResult.Failure("Unknown")
        }
    }

    suspend fun logout(): UserLogoutResult {
        val client = clients.client.value ?: return UserLogoutResult.Failure("接続先が未設定")
        val response = client.mutation(UserLogoutMutation()).execute()
        if (response.exception != null || response.hasErrors()) return UserLogoutResult.Failure(response.failureMessage())
        if (response.data?.user?.logout != true) return UserLogoutResult.Failure(response.failureMessage())

        return UserLogoutResult.Success
    }

    suspend fun loginState(): UserLoginStateResult {
        // 接続先が無ければセッションも無い。接続先はログイン画面で決める
        val client = clients.awaitLoadedClient() ?: return UserLoginStateResult.NotLoggedIn
        val response = client.query(LoginGateQuery()).execute()
        if (response.exception != null || response.hasErrors()) return UserLoginStateResult.Failure(response.failureMessage())
        val data = response.data ?: return UserLoginStateResult.Failure(response.failureMessage())

        return if (data.userInfo == null) {
            UserLoginStateResult.NotLoggedIn
        } else {
            UserLoginStateResult.LoggedIn
        }
    }

    suspend fun userProfile(username: String): UserProfileResult {
        val client = clients.awaitLoadedClient() ?: return UserProfileResult.Failure("接続先が未設定")
        val response = client.query(UserProfileQuery(username = username)).execute()
        if (response.exception != null || response.hasErrors()) return UserProfileResult.Failure(response.failureMessage())
        val data = response.data ?: return UserProfileResult.Failure(response.failureMessage())
        val user = data.user ?: return UserProfileResult.NotFound

        return UserProfileResult.Found(username = user.username, iconUrl = user.iconUrl)
    }

    suspend fun searchUsers(
        query: String,
        after: String?,
    ): UserSearchResult {
        val client = clients.awaitLoadedClient() ?: return UserSearchResult.Failure("接続先が未設定")
        val response = client.query(HomeScreenSearchUsersQuery(query = query, after = Optional.presentIfNotNull(after))).execute()
        if (response.exception != null || response.hasErrors()) return UserSearchResult.Failure(response.failureMessage())
        val searchUsers = response.data?.searchUsers ?: return UserSearchResult.Failure(response.failureMessage())

        return UserSearchResult.Success(
            users = searchUsers.users.map {
                UserSearchResult.User(
                    actorUri = it.actorUri,
                    following = it.following,
                    username = it.username,
                    host = it.host,
                    isLocal = it.isLocal,
                    displayName = it.displayName,
                    iconUrl = it.iconUrl,
                )
            },
            nextCursor = searchUsers.nextCursor,
        )
    }

    suspend fun resolveUser(query: String): UserResolveResult {
        val client = clients.awaitLoadedClient() ?: return UserResolveResult.Failure("接続先が未設定")
        val response = client.query(HomeScreenResolveUserQuery(query = query)).execute()
        if (response.exception != null || response.hasErrors()) return UserResolveResult.Failure(response.failureMessage())
        val data = response.data ?: return UserResolveResult.Failure(response.failureMessage())
        val user = data.resolveUser ?: return UserResolveResult.NotFound

        return UserResolveResult.Found(
            UserSearchResult.User(
                actorUri = user.actorUri,
                following = user.following,
                username = user.username,
                host = user.host,
                isLocal = user.isLocal,
                displayName = user.displayName,
                iconUrl = user.iconUrl,
            ),
        )
    }

    suspend fun postNote(
        body: String,
        requestId: String,
    ): UserPostNoteResult {
        val client = clients.client.value ?: return UserPostNoteResult.Failure("接続先が未設定")
        val response = client.mutation(UserPostNoteMutation(body = body, requestId = requestId)).execute()
        if (response.exception != null || response.hasErrors()) return UserPostNoteResult.Failure(response.failureMessage())
        val postNote = response.data?.user?.postNote ?: return UserPostNoteResult.Failure(response.failureMessage())

        return when (postNote.failure) {
            null -> UserPostNoteResult.Success
            UserPostNoteFailure.NOT_LOGGED_IN -> UserPostNoteResult.NotLoggedIn
            UserPostNoteFailure.EMPTY_BODY -> UserPostNoteResult.EmptyBody
            UserPostNoteFailure.TOO_LONG -> UserPostNoteResult.TooLong
            UserPostNoteFailure.FEDERATION_DISABLED -> UserPostNoteResult.FederationDisabled
            UserPostNoteFailure.UNKNOWN__ -> UserPostNoteResult.Failure("Unknown")
        }
    }

    suspend fun myProfile(): UserMyProfileResult {
        val client = clients.awaitLoadedClient() ?: return UserMyProfileResult.Failure("接続先が未設定")
        val response = client.query(MyProfileQuery()).execute()
        if (response.exception != null || response.hasErrors()) return UserMyProfileResult.Failure(response.failureMessage())
        val data = response.data ?: return UserMyProfileResult.Failure(response.failureMessage())
        val profile = data.profile ?: return UserMyProfileResult.NotLoggedIn

        return UserMyProfileResult.Found(
            username = profile.username,
            displayName = profile.displayName,
            iconUrl = profile.iconUrl,
            followingCount = profile.followingCount,
            followersCount = profile.followersCount,
        )
    }

    /**
     * @param displayName 空なら未設定に戻す
     */
    suspend fun updateProfile(displayName: String): UserUpdateProfileResult {
        val client = clients.client.value ?: return UserUpdateProfileResult.Failure("接続先が未設定")
        val response = client.mutation(UpdateProfileMutation(UpdateProfileQuery(displayName = Optional.present(displayName)))).execute()
        if (response.exception != null || response.hasErrors()) return UserUpdateProfileResult.Failure(response.failureMessage())
        val updateProfile = response.data?.user?.updateProfile ?: return UserUpdateProfileResult.Failure(response.failureMessage())

        return when (updateProfile.failure) {
            null -> UserUpdateProfileResult.Success
            UpdateProfileFailure.NOT_LOGGED_IN -> UserUpdateProfileResult.NotLoggedIn
            UpdateProfileFailure.TOO_LONG -> UserUpdateProfileResult.TooLong
            UpdateProfileFailure.FEDERATION_DISABLED -> UserUpdateProfileResult.FederationDisabled
            UpdateProfileFailure.UNKNOWN__ -> UserUpdateProfileResult.Failure("Unknown")
        }
    }

    /**
     * @param icon 画像ファイルの中身
     */
    suspend fun updateProfileIcon(icon: ByteArray): UserUpdateProfileIconResult {
        val client = clients.client.value ?: return UserUpdateProfileIconResult.Failure("接続先が未設定")
        val response = client.mutation(UpdateProfileIconMutation(icon = Base64.Default.encode(icon))).execute()
        if (response.exception != null || response.hasErrors()) return UserUpdateProfileIconResult.Failure(response.failureMessage())
        val updateProfileIcon = response.data?.user?.updateProfileIcon ?: return UserUpdateProfileIconResult.Failure(response.failureMessage())

        return when (updateProfileIcon.failure) {
            null -> UserUpdateProfileIconResult.Success(iconUrl = updateProfileIcon.iconUrl)
            UpdateProfileIconFailure.NOT_LOGGED_IN -> UserUpdateProfileIconResult.NotLoggedIn
            UpdateProfileIconFailure.TOO_LARGE -> UserUpdateProfileIconResult.TooLarge
            UpdateProfileIconFailure.DIMENSIONS_TOO_LARGE -> UserUpdateProfileIconResult.DimensionsTooLarge
            UpdateProfileIconFailure.UNSUPPORTED_IMAGE -> UserUpdateProfileIconResult.UnsupportedImage
            UpdateProfileIconFailure.UNSUPPORTED_OPERATION -> UserUpdateProfileIconResult.UnsupportedOperation
            UpdateProfileIconFailure.INTERNAL_SERVER_ERROR -> UserUpdateProfileIconResult.InternalServerError
            UpdateProfileIconFailure.FEDERATION_DISABLED -> UserUpdateProfileIconResult.FederationDisabled
            UpdateProfileIconFailure.UNKNOWN__ -> UserUpdateProfileIconResult.Failure("Unknown")
        }
    }

    data class FollowingChange(
        val actorUri: String,
        val following: Boolean,
    )

    private fun ActorListFields.toUserFollowListResult(): UserFollowListResult =
        UserFollowListResult.Success(
            users = users.map {
                UserFollowListResult.User(
                    actorUri = it.actorUri,
                    following = it.following,
                    username = it.username,
                    host = it.host,
                    displayName = it.displayName,
                    iconUrl = it.iconUrl,
                )
            },
            nextCursor = nextCursor,
        )

    private fun ApolloResponse<*>.failureMessage(): String {
        return exception?.message
            ?: errors?.joinToString("\n") { it.message }?.takeIf { it.isNotEmpty() }
            ?: "ネットワークエラー"
    }
}
