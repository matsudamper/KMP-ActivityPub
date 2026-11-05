package net.matsudamper.kmp.activitypub.frontend.logic.user

import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.DefaultUpload
import com.apollographql.apollo.api.Optional
import com.apollographql.cache.normalized.apolloStore
import net.matsudamper.kmp.activitypub.frontend.graphql.DeleteNoteMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowerUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowingUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenResolveUserQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenSearchUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeTimelineLinkPreviewsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeTimelinePostsSubscription
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeTimelineQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.LoginGateQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.MyProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.SetFollowingMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UpdateProfileIconMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UpdateProfileMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UploadNoteImageMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLoginMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLogoutMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserPostNoteMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.ActorListFields
import net.matsudamper.kmp.activitypub.frontend.graphql.type.DeleteNoteFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UpdateProfileFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UpdateProfileIconFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UpdateProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UploadNoteImageFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserLoginFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserLoginQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UserPostNoteFailure
import net.matsudamper.kmp.activitypub.frontend.logic.CursorPagingModel
import net.matsudamper.kmp.activitypub.frontend.logic.GraphQlClientSource
import net.matsudamper.kmp.activitypub.frontend.logic.closeSubscriptionConnection
import net.matsudamper.kmp.activitypub.frontend.logic.failureMessage

class UserApi(
    private val clients: GraphQlClientSource,
) {
    private val followingChangesFlow = MutableSharedFlow<FollowingChange>()
    val followingChanges = followingChangesFlow.asSharedFlow()
    val ready: Flow<Boolean> = clients.client.map { it != null }.distinctUntilChanged()

    fun isReady(): Boolean = clients.client.value != null

    fun homeTimelinePaging(): CursorPagingModel<HomeTimelineQuery.Data> {
        return CursorPagingModel(
            clients = clients,
            firstQuery = HomeTimelineQuery(cursor = Optional.present(null), limit = HOME_TIMELINE_PAGE_SIZE),
            queryAfter = { cursor -> HomeTimelineQuery(cursor = Optional.present(cursor), limit = HOME_TIMELINE_PAGE_SIZE) },
            nextCursorOf = { it.userInfo?.homeTimeline?.nextCursor },
            appendPage = { before, page ->
                val beforeUserInfo = before.userInfo ?: return@CursorPagingModel null
                val pageTimeline = page.userInfo?.homeTimeline ?: return@CursorPagingModel null
                val beforeTimeline = beforeUserInfo.homeTimeline ?: return@CursorPagingModel null
                before.copy(
                    userInfo = beforeUserInfo.copy(
                        homeTimeline = beforeTimeline.copy(
                            posts = beforeTimeline.posts + pageTimeline.posts,
                            nextCursor = pageTimeline.nextCursor,
                        ),
                    ),
                )
            },
        )
    }

    /**
     * @param postId [UserHomeTimelineResult.Post.id]
     */
    suspend fun linkPreviews(postId: String): UserLinkPreviewsResult {
        val client = clients.awaitLoadedClient() ?: return UserLinkPreviewsResult.Failure("接続先が未設定")
        val response = client.query(HomeTimelineLinkPreviewsQuery(postId = postId)).execute()
        if (response.exception != null || response.hasErrors()) return UserLinkPreviewsResult.Failure(response.failureMessage())
        val data = response.data ?: return UserLinkPreviewsResult.Failure(response.failureMessage())
        val previews = data.userInfo?.linkPreviews ?: return UserLinkPreviewsResult.NotLoggedIn
        return UserLinkPreviewsResult.Success(
            previews = previews.map {
                UserLinkPreviewsResult.LinkPreview(url = it.url, title = it.title, siteName = it.siteName, imageUrl = it.imageUrl)
            },
        )
    }

    /**
     * ホームタイムラインに新しく載った投稿を流し続ける。
     * 切れたら間隔を倍に延ばしながらつなぎ直し、続けて失敗したら [HomeTimelineStreamEvent.GaveUp] を流して終わる
     */
    fun homeTimelineStream(): Flow<HomeTimelineStreamEvent> = flow {
        var consecutiveFailureCount = 0
        while (true) {
            emit(HomeTimelineStreamEvent.Subscribing)
            val client = clients.awaitLoadedClient()
            val connectedAt = TimeSource.Monotonic.markNow()
            val receivedPostCount = if (client == null) 0 else collectHomeTimelinePosts(client)
            val connectionWasHealthy = receivedPostCount > 0 || connectedAt.elapsedNow() >= HOME_TIMELINE_HEALTHY_CONNECTION_DURATION
            consecutiveFailureCount = if (connectionWasHealthy) 0 else consecutiveFailureCount + 1
            if (consecutiveFailureCount >= HOME_TIMELINE_MAX_CONSECUTIVE_FAILURES) break
            emit(HomeTimelineStreamEvent.WaitingToReconnect)
            delay(HOME_TIMELINE_RECONNECT_BASE_DELAY * (1 shl (consecutiveFailureCount - 1).coerceAtLeast(0)))
        }
        emit(HomeTimelineStreamEvent.GaveUp)
    }

    private suspend fun FlowCollector<HomeTimelineStreamEvent>.collectHomeTimelinePosts(client: ApolloClient): Int {
        val receivedPostCount = client.subscription(HomeTimelinePostsSubscription()).toFlow()
            .mapNotNull { response -> response.data?.homeTimelinePosts }
            .onEach { post ->
                emit(
                    HomeTimelineStreamEvent.Arrived(
                        UserHomeTimelineResult.Post(
                            id = post.id,
                            username = post.username,
                            host = post.host,
                            displayName = post.displayName,
                            iconUrl = post.iconUrl,
                            body = UserPostBody(
                                content = post.content,
                                publishedAt = post.publishedAt,
                                summary = post.summary,
                                sensitive = post.sensitive,
                                linkUrls = post.linkUrls,
                                images = post.images.map { UserPostImage(url = it.url, description = it.description) },
                                favouriteCount = post.favouriteCount,
                                stamps = post.stamps.map { UserPostStamp(emoji = it.emoji, imageUrl = it.imageUrl, count = it.count) },
                            ),
                            deletableNoteId = post.deletableNoteId,
                        ),
                    ),
                )
            }
            .count()
        // パスキーでのログインなど login・logout を通らずにセッションが変わることもあるので、購読が終わるたびに張り直させる
        client.closeSubscriptionConnection()
        return receivedPostCount
    }

    suspend fun setFollowing(actorUri: String, followed: Boolean): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(SetFollowingMutation(actorUri = actorUri, followed = followed)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        if (response.data?.user?.setFollowing != true) return "フォロー状態を変更できませんでした"
        followingChangesFlow.emit(FollowingChange(actorUri = actorUri, following = followed))
        return null
    }

    fun followingUsersPaging(): CursorPagingModel<FollowingUsersQuery.Data> {
        return CursorPagingModel(
            clients = clients,
            firstQuery = FollowingUsersQuery(after = Optional.present(null)),
            queryAfter = { cursor -> FollowingUsersQuery(after = Optional.present(cursor)) },
            nextCursorOf = { it.followingUsers?.actorListFields?.nextCursor },
            appendPage = { before, page ->
                val beforeList = before.followingUsers ?: return@CursorPagingModel null
                val pageList = page.followingUsers ?: return@CursorPagingModel null
                before.copy(followingUsers = beforeList.copy(actorListFields = beforeList.actorListFields.append(pageList.actorListFields)))
            },
        )
    }

    fun followerUsersPaging(): CursorPagingModel<FollowerUsersQuery.Data> {
        return CursorPagingModel(
            clients = clients,
            firstQuery = FollowerUsersQuery(after = Optional.present(null)),
            queryAfter = { cursor -> FollowerUsersQuery(after = Optional.present(cursor)) },
            nextCursorOf = { it.followerUsers?.actorListFields?.nextCursor },
            appendPage = { before, page ->
                val beforeList = before.followerUsers ?: return@CursorPagingModel null
                val pageList = page.followerUsers ?: return@CursorPagingModel null
                before.copy(followerUsers = beforeList.copy(actorListFields = beforeList.actorListFields.append(pageList.actorListFields)))
            },
        )
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
            null -> {
                // 別のアカウントで取得した内容を出さない
                client.apolloStore.clearAll()
                client.closeSubscriptionConnection()
                UserLoginResult.Success
            }

            UserLoginFailure.WRONG_USERNAME_OR_PASSWORD -> UserLoginResult.WrongUsernameOrPassword

            UserLoginFailure.UNKNOWN__ -> UserLoginResult.Failure("Unknown")
        }
    }

    suspend fun logout(): UserLogoutResult {
        val client = clients.client.value ?: return UserLogoutResult.Failure("接続先が未設定")
        val response = client.mutation(UserLogoutMutation()).execute()
        if (response.exception != null || response.hasErrors()) return UserLogoutResult.Failure(response.failureMessage())
        if (response.data?.user?.logout != true) return UserLogoutResult.Failure(response.failureMessage())
        client.apolloStore.clearAll()
        client.closeSubscriptionConnection()

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
        val profile = data.userProfile ?: return UserProfileResult.NotFound
        val user = profile.user

        return UserProfileResult.Found(
            actorUri = user.actorUri,
            username = user.username,
            host = user.host,
            displayName = user.displayName,
            iconUrl = user.iconUrl,
            following = user.following,
            biography = profile.biography,
            posts = profile.posts.map {
                UserProfileResult.Post(
                    linkPreviewId = it.linkPreviewId,
                    body = UserPostBody(
                        content = it.content,
                        publishedAt = it.publishedAt,
                        summary = it.summary,
                        sensitive = it.sensitive,
                        linkUrls = it.linkUrls,
                        images = it.images.map { image -> UserPostImage(url = image.url, description = image.description) },
                        favouriteCount = it.favouriteCount,
                        stamps = it.stamps.map { stamp -> UserPostStamp(emoji = stamp.emoji, imageUrl = stamp.imageUrl, count = stamp.count) },
                    ),
                )
            },
        )
    }

    fun searchUsersPaging(query: String): CursorPagingModel<HomeScreenSearchUsersQuery.Data> {
        return CursorPagingModel(
            clients = clients,
            firstQuery = HomeScreenSearchUsersQuery(query = query, after = Optional.present(null)),
            queryAfter = { cursor -> HomeScreenSearchUsersQuery(query = query, after = Optional.present(cursor)) },
            nextCursorOf = { it.searchUsers.nextCursor },
            appendPage = { before, page ->
                before.copy(
                    searchUsers = before.searchUsers.copy(
                        users = before.searchUsers.users + page.searchUsers.users,
                        nextCursor = page.searchUsers.nextCursor,
                    ),
                )
            },
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
        imageIds: List<String>,
    ): UserPostNoteResult {
        val client = clients.client.value ?: return UserPostNoteResult.Failure("接続先が未設定")
        val response = client.mutation(UserPostNoteMutation(body = body, requestId = requestId, imageIds = imageIds)).execute()
        if (response.exception != null || response.hasErrors()) return UserPostNoteResult.Failure(response.failureMessage())
        val postNote = response.data?.user?.postNote ?: return UserPostNoteResult.Failure(response.failureMessage())

        return when (postNote.failure) {
            null -> UserPostNoteResult.Success
            UserPostNoteFailure.NOT_LOGGED_IN -> UserPostNoteResult.NotLoggedIn
            UserPostNoteFailure.EMPTY_BODY -> UserPostNoteResult.EmptyBody
            UserPostNoteFailure.TOO_LONG -> UserPostNoteResult.TooLong
            UserPostNoteFailure.FEDERATION_DISABLED -> UserPostNoteResult.FederationDisabled
            UserPostNoteFailure.TOO_MANY_IMAGES -> UserPostNoteResult.TooManyImages
            UserPostNoteFailure.IMAGE_NOT_FOUND -> UserPostNoteResult.ImageNotFound
            UserPostNoteFailure.UNKNOWN__ -> UserPostNoteResult.Failure("Unknown")
        }
    }

    /**
     * @param noteId [UserHomeTimelineResult.Post.deletableNoteId]
     * @return 消せなければ、その理由
     */
    suspend fun deleteNote(noteId: String): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(DeleteNoteMutation(noteId = noteId)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        val deleteNote = response.data?.user?.deleteNote ?: return response.failureMessage()
        return when (deleteNote.failure) {
            null -> null
            DeleteNoteFailure.NOT_LOGGED_IN -> "ログインしていません"
            DeleteNoteFailure.NOT_FOUND -> "投稿が見つかりません"
            DeleteNoteFailure.FEDERATION_DISABLED -> "このサーバーでは投稿を削除できません"
            DeleteNoteFailure.INTERNAL_SERVER_ERROR -> "投稿を削除できませんでした。時間をおいて試してください"
            DeleteNoteFailure.UNKNOWN__ -> "Unknown"
        }
    }

    suspend fun uploadNoteImage(image: UserPostNoteImage): UserUploadNoteImageResult {
        val client = clients.client.value ?: return UserUploadNoteImageResult.Failure("接続先が未設定")
        val upload =
            DefaultUpload.Builder()
                .content(image.content::writeTo)
                .contentLength(image.content.byteCount)
                .contentType("application/octet-stream")
                .fileName("image")
                .build()
        val mutation = UploadNoteImageMutation(image = upload, description = Optional.presentIfNotNull(image.description))
        // multipart はプリフライト無しで別オリジンから送れるので、サーバーはこのヘッダーで弾いている
        val response = client.mutation(mutation).addHttpHeader("Apollo-Require-Preflight", "true").execute()
        if (response.exception != null || response.hasErrors()) return UserUploadNoteImageResult.Failure(response.failureMessage())
        val uploadNoteImage = response.data?.user?.uploadNoteImage ?: return UserUploadNoteImageResult.Failure(response.failureMessage())

        return when (uploadNoteImage.failure) {
            null -> uploadNoteImage.imageId?.let { UserUploadNoteImageResult.Success(imageId = it) } ?: UserUploadNoteImageResult.Failure(response.failureMessage())
            UploadNoteImageFailure.NOT_LOGGED_IN -> UserUploadNoteImageResult.NotLoggedIn
            UploadNoteImageFailure.TOO_LARGE -> UserUploadNoteImageResult.TooLarge
            UploadNoteImageFailure.UNSUPPORTED_IMAGE -> UserUploadNoteImageResult.UnsupportedImage
            UploadNoteImageFailure.DESCRIPTION_TOO_LONG -> UserUploadNoteImageResult.DescriptionTooLong
            UploadNoteImageFailure.UNSUPPORTED_OPERATION -> UserUploadNoteImageResult.UnsupportedOperation
            UploadNoteImageFailure.INTERNAL_SERVER_ERROR -> UserUploadNoteImageResult.InternalServerError
            UploadNoteImageFailure.UNKNOWN__ -> UserUploadNoteImageResult.Failure("Unknown")
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

    private fun ActorListFields.append(page: ActorListFields): ActorListFields =
        copy(users = users + page.users, nextCursor = page.nextCursor)

    private companion object {
        const val HOME_TIMELINE_PAGE_SIZE = 40
        const val HOME_TIMELINE_MAX_CONSECUTIVE_FAILURES = 5

        /**
         * 失敗の回数に応じて倍にしていく。5 回続けて失敗すると、諦めるまでに 5 + 10 + 20 + 40 秒待つ
         */
        val HOME_TIMELINE_RECONNECT_BASE_DELAY = 5.seconds

        /**
         * 投稿が来なくても、これだけ切れずにいられたなら接続できていたとみなし、失敗の回数を数え直す
         */
        val HOME_TIMELINE_HEALTHY_CONNECTION_DURATION = 1.minutes
    }
}
