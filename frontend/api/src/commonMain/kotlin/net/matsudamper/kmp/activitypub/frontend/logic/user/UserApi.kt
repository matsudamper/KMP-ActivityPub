package net.matsudamper.kmp.activitypub.frontend.logic.user

import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.DefaultUpload
import com.apollographql.apollo.api.Optional
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.doNotStore
import com.apollographql.cache.normalized.fetchPolicy
import net.matsudamper.kmp.activitypub.frontend.graphql.AccountEventsSubscription
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminAccessQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.BlockServerMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.BlockedServersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.CreateFollowingGroupMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.DeleteNoteMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowerUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowingGroupsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowingUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenResolveUserQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenSearchUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeTimelineLinkPreviewsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.LoginGateQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.MyProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.NotificationsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.PostDetailScreenQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.RemoteProfileRefreshSubscription
import net.matsudamper.kmp.activitypub.frontend.graphql.RenameFollowingGroupMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.ReplyTargetQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.SetBoostedMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.SetFollowingMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.SetLikedMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.SetPinnedMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.TimelineQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.UnblockServerMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UpdateProfileIconMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UpdateProfileMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UploadNoteImageMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLoginMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLogoutMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserPostNoteMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserProfileLikesQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.UserProfilePostsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.UserProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.ActorListFields
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.ProfilePostFields
import net.matsudamper.kmp.activitypub.frontend.graphql.type.BlockServerFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.CreateFollowingGroupFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.DeleteNoteFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.ProfileLinkInput
import net.matsudamper.kmp.activitypub.frontend.graphql.type.RenameFollowingGroupFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.SetBoostedFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.SetLikedFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.SetPinnedFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UnblockServerFailure
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
    private val followingGroupsStateFlow = MutableStateFlow(FollowingGroupsState(mainGroupName = DEFAULT_MAIN_GROUP_NAME, groups = listOf(), version = 0))

    /**
     * 購読を仕分けるグループ。作った順。[loadFollowingGroups] で読み直す
     */
    val followingGroups: Flow<List<UserFollowingGroup>> = followingGroupsStateFlow.map { it.groups }.distinctUntilChanged()

    /**
     * グループに入れていない購読を入れるメインの名前。[loadFollowingGroups] で読み直す
     */
    val mainGroupName: Flow<String> = followingGroupsStateFlow.map { it.mainGroupName }.distinctUntilChanged()
    private val deletedNoteIdsFlow = MutableSharedFlow<String>()

    /**
     * [deleteNote] で消せた投稿の id。どの画面で消しても、投稿を出している画面がここで外す
     */
    val deletedNoteIds = deletedNoteIdsFlow.asSharedFlow()
    private val pinChangesFlow = MutableSharedFlow<PinChange>()

    /**
     * [setPinned] で変えられた固定。どの画面で変えても、プロフィールの投稿を出している画面がここで並べ直す
     */
    val pinChanges = pinChangesFlow.asSharedFlow()
    private val boostChangesFlow = MutableSharedFlow<BoostChange>()

    /**
     * [setBoosted] で変えられたブースト。どの画面で変えても、同じ投稿を出している画面がここで表示を合わせる
     */
    val boostChanges = boostChangesFlow.asSharedFlow()
    val ready: Flow<Boolean> = clients.client.map { it != null }.distinctUntilChanged()

    fun isReady(): Boolean = clients.client.value != null

    /**
     * @param followingGroupId 読む購読先。null ならメイン
     */
    fun timelinePaging(followingGroupId: String?): CursorPagingModel<TimelineQuery.Data> {
        return CursorPagingModel(
            clients = clients,
            firstQuery = TimelineQuery(followingGroupId = Optional.present(followingGroupId), after = Optional.present(null), limit = TIMELINE_PAGE_SIZE),
            queryAfter = { cursor -> TimelineQuery(followingGroupId = Optional.present(followingGroupId), after = Optional.present(cursor), limit = TIMELINE_PAGE_SIZE) },
            nextCursorOf = { it.userInfo?.timeline?.nextCursor },
            appendPage = { before, page ->
                val beforeUserInfo = before.userInfo ?: return@CursorPagingModel null
                val pageTimeline = page.userInfo?.timeline ?: return@CursorPagingModel null
                val beforeTimeline = beforeUserInfo.timeline ?: return@CursorPagingModel null
                before.copy(
                    userInfo = beforeUserInfo.copy(
                        timeline = beforeTimeline.copy(
                            posts = beforeTimeline.posts + pageTimeline.posts,
                            nextCursor = pageTimeline.nextCursor,
                        ),
                    ),
                )
            },
        )
    }

    fun notificationsPaging(): CursorPagingModel<NotificationsQuery.Data> {
        return CursorPagingModel(
            clients = clients,
            firstQuery = NotificationsQuery(after = Optional.present(null), limit = TIMELINE_PAGE_SIZE),
            queryAfter = { cursor -> NotificationsQuery(after = Optional.present(cursor), limit = TIMELINE_PAGE_SIZE) },
            nextCursorOf = { it.userInfo?.notifications?.nextCursor },
            appendPage = { before, page -> before.withNextPage(page) },
        )
    }

    /**
     * 購読で足し込むタイムラインの通知と同じクエリなので、そちらのキャッシュを書き換えないようキャッシュに入れない
     *
     * @param after 続きを読むときは前のページの nextCursor。1 ページ目は null
     */
    suspend fun fetchNotificationsPage(after: String?): UserNotificationsPageResult {
        val client = clients.awaitLoadedClient() ?: return UserNotificationsPageResult.Failure("接続先が未設定")
        val response = client.query(NotificationsQuery(after = Optional.present(after), limit = TIMELINE_PAGE_SIZE))
            .fetchPolicy(FetchPolicy.NetworkOnly)
            .doNotStore(true)
            .execute()
        if (response.exception != null || response.hasErrors()) return UserNotificationsPageResult.Failure(response.failureMessage())
        val data = response.data ?: return UserNotificationsPageResult.Failure(response.failureMessage())
        return UserNotificationsPageResult.Success(data)
    }

    /**
     * @param postId [UserHomeTimelineResult.Post.id] か [UserProfileResult.Post.postId]
     */
    suspend fun linkPreviews(postId: String): UserLinkPreviewsResult {
        val client = clients.awaitLoadedClient() ?: return UserLinkPreviewsResult.Failure("接続先が未設定")
        val response = client.query(HomeTimelineLinkPreviewsQuery(postId = postId)).execute()
        if (response.exception != null || response.hasErrors()) return UserLinkPreviewsResult.Failure(response.failureMessage())
        val data = response.data ?: return UserLinkPreviewsResult.Failure(response.failureMessage())
        val previews = data.userInfo?.linkPreviews ?: return UserLinkPreviewsResult.NotLoggedIn
        return UserLinkPreviewsResult.Success(
            previews = previews.map {
                UserLinkPreviewsResult.LinkPreview(url = it.url, title = it.title, siteName = it.siteName, imageUrl = it.imageUrl, faviconUrl = it.faviconUrl)
            },
        )
    }

    /**
     * @param postId [UserHomeTimelineResult.Post.id] か [UserProfileResult.Post.postId]
     */
    suspend fun postDetail(postId: String): UserPostDetailResult {
        val client = clients.awaitLoadedClient() ?: return UserPostDetailResult.Failure("接続先が未設定")
        val response = client.query(PostDetailScreenQuery(id = postId)).execute()
        if (response.exception != null || response.hasErrors()) return UserPostDetailResult.Failure(response.failureMessage())
        val data = response.data ?: return UserPostDetailResult.Failure(response.failureMessage())
        val userInfo = data.userInfo ?: return UserPostDetailResult.NotLoggedIn
        val post = userInfo.timelinePost ?: return UserPostDetailResult.NotFound
        return UserPostDetailResult.Found(post.timelinePostFields.toPost())
    }

    /**
     * 返信先の投稿。返した投稿の [UserHomeTimelineResult.Post.id] を渡せば、さらに返信先をたどれる
     *
     * @param postId [UserHomeTimelineResult.Post.id]
     * @return 返信でないか、返信先を読めなければ [UserPostDetailResult.NotFound]
     */
    suspend fun replyTarget(postId: String): UserPostDetailResult {
        val client = clients.awaitLoadedClient() ?: return UserPostDetailResult.Failure("接続先が未設定")
        val response = client.query(ReplyTargetQuery(postId = postId)).execute()
        if (response.exception != null || response.hasErrors()) return UserPostDetailResult.Failure(response.failureMessage())
        val data = response.data ?: return UserPostDetailResult.Failure(response.failureMessage())
        val userInfo = data.userInfo ?: return UserPostDetailResult.NotLoggedIn
        val post = userInfo.replyTarget ?: return UserPostDetailResult.NotFound
        return UserPostDetailResult.Found(post.timelinePostFields.toPost())
    }

    /**
     * ログイン中のアカウントに起きた変化（新着・削除・通知）を流し続ける。
     * 切れたら間隔を倍に延ばしながらつなぎ直し、続けて失敗したら [AccountStreamEvent.GaveUp] を流して終わる
     */
    fun accountEventStream(): Flow<AccountStreamEvent> = flow {
        var consecutiveFailureCount = 0
        while (true) {
            emit(AccountStreamEvent.Subscribing)
            val client = clients.awaitLoadedClient()
            val connectedAt = TimeSource.Monotonic.markNow()
            val receivedEventCount = if (client == null) 0 else collectAccountEvents(client)
            val connectionWasHealthy = receivedEventCount > 0 || connectedAt.elapsedNow() >= ACCOUNT_EVENT_HEALTHY_CONNECTION_DURATION
            consecutiveFailureCount = if (connectionWasHealthy) 0 else consecutiveFailureCount + 1
            if (consecutiveFailureCount >= ACCOUNT_EVENT_MAX_CONSECUTIVE_FAILURES) break
            emit(AccountStreamEvent.WaitingToReconnect)
            delay(ACCOUNT_EVENT_RECONNECT_BASE_DELAY * (1 shl (consecutiveFailureCount - 1).coerceAtLeast(0)))
        }
        emit(AccountStreamEvent.GaveUp)
    }

    /**
     * @param groupId 購読するときに入れるグループ。null ならメイン。購読済みならグループだけ入れ直す
     * @return 失敗したときに見せる文言。変えられたら null
     */
    suspend fun setFollowing(actorUri: String, followed: Boolean, groupId: String?): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(SetFollowingMutation(actorUri = actorUri, followed = followed, groupId = Optional.present(groupId))).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        if (response.data?.user?.setFollowing != true) return "購読状態を変更できませんでした"
        followingChangesFlow.emit(FollowingChange(actorUri = actorUri, following = followed, groupId = if (followed) groupId else null))
        return null
    }

    fun currentFollowingGroups(): List<UserFollowingGroup> = followingGroupsStateFlow.value.groups

    fun currentMainGroupName(): String = followingGroupsStateFlow.value.mainGroupName

    /**
     * 読めなかったときは空にする。ここを通らずにアカウントが変わることもあり、前の内容が別のアカウントのものかもしれない。
     * 読んでいる間にアカウントが変わるか、グループを作ったら、読んだ内容は古いので捨てる
     */
    suspend fun loadFollowingGroups() {
        val startedVersion = followingGroupsStateFlow.value.version
        val client = clients.awaitLoadedClient()
        val userInfo = client?.query(FollowingGroupsQuery())?.execute()?.data?.userInfo
        val groups = userInfo?.followingGroups
            .orEmpty()
            .map { UserFollowingGroup(id = it.id, name = it.name) }
        val mainGroupName = userInfo?.mainGroupName ?: DEFAULT_MAIN_GROUP_NAME
        followingGroupsStateFlow.update { state ->
            if (state.version == startedVersion) FollowingGroupsState(mainGroupName = mainGroupName, groups = groups, version = state.version + 1) else state
        }
    }

    /**
     * ログインしているアカウントが変わったときに、前のアカウントのグループを出さないよう呼ぶ
     */
    fun forgetFollowingGroups() {
        followingGroupsStateFlow.update { FollowingGroupsState(mainGroupName = DEFAULT_MAIN_GROUP_NAME, groups = listOf(), version = it.version + 1) }
    }

    suspend fun createFollowingGroup(name: String): UserCreateFollowingGroupResult {
        val client = clients.client.value ?: return UserCreateFollowingGroupResult.Failure("接続先が未設定")
        val response = client.mutation(CreateFollowingGroupMutation(name = name)).execute()
        if (response.exception != null || response.hasErrors()) return UserCreateFollowingGroupResult.Failure(response.failureMessage())
        val result = response.data?.user?.createFollowingGroup ?: return UserCreateFollowingGroupResult.Failure(response.failureMessage())
        val group = result.group
        if (group != null) {
            val created = UserFollowingGroup(id = group.id, name = group.name)
            followingGroupsStateFlow.update { state ->
                val groups = if (state.groups.any { it.id == created.id }) state.groups else state.groups + created
                state.copy(groups = groups, version = state.version + 1)
            }
            return UserCreateFollowingGroupResult.Created(created)
        }
        return UserCreateFollowingGroupResult.Failure(
            when (result.failure) {
                CreateFollowingGroupFailure.NOT_LOGGED_IN -> "ログインしていません"
                CreateFollowingGroupFailure.INVALID_NAME -> "名前は 1〜30 文字にしてください"
                CreateFollowingGroupFailure.UNKNOWN__, null -> "グループを作れませんでした"
            },
        )
    }

    /**
     * 変えたら読み直す。送っている間に始まった読み込みや、アカウントの切り替えで捨てられた内容を取り戻すため
     *
     * @param groupId null ならメインの名前を変える
     * @return 失敗したときに見せる文言。変えられたら null
     */
    suspend fun renameFollowingGroup(groupId: String?, name: String): String? {
        val startedVersion = followingGroupsStateFlow.value.version
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(RenameFollowingGroupMutation(groupId = Optional.present(groupId), name = name)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        val result = response.data?.user?.renameFollowingGroup ?: return response.failureMessage()
        return when (result.failure) {
            null -> {
                val trimmedName = name.trim()
                followingGroupsStateFlow.update { state ->
                    when {
                        state.version != startedVersion -> state

                        groupId == null -> state.copy(mainGroupName = trimmedName, version = state.version + 1)

                        else -> state.copy(
                            groups = state.groups.map { if (it.id == groupId) it.copy(name = trimmedName) else it },
                            version = state.version + 1,
                        )
                    }
                }
                loadFollowingGroups()
                null
            }

            RenameFollowingGroupFailure.NOT_LOGGED_IN -> "ログインしていません"

            RenameFollowingGroupFailure.INVALID_NAME -> "名前は 1〜30 文字にしてください"

            RenameFollowingGroupFailure.NOT_FOUND -> "購読先が見つかりませんでした"

            RenameFollowingGroupFailure.DUPLICATE_NAME -> "同じ名前の購読先があります"

            RenameFollowingGroupFailure.UNKNOWN__ -> "名前を変えられませんでした"
        }
    }

    suspend fun blockedServers(): UserBlockedServersResult {
        val client = clients.client.value ?: return UserBlockedServersResult.Failure("接続先が未設定")
        val response = client.query(BlockedServersQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()
        if (response.exception != null || response.hasErrors()) return UserBlockedServersResult.Failure(response.failureMessage())
        val userInfo = response.data?.userInfo ?: return UserBlockedServersResult.Failure("ログインしていません")
        return UserBlockedServersResult.Success(userInfo.blockedServers)
    }

    /**
     * @param server ホスト名か、そのサーバーの URL やユーザーのアカウント名
     */
    suspend fun blockServer(server: String): UserBlockServerResult {
        val client = clients.client.value ?: return UserBlockServerResult.Failure("接続先が未設定")
        val response = client.mutation(BlockServerMutation(server = server)).execute()
        if (response.exception != null || response.hasErrors()) return UserBlockServerResult.Failure(response.failureMessage())
        val result = response.data?.user?.blockServer ?: return UserBlockServerResult.Failure(response.failureMessage())
        val host = result.host
        if (host != null) return UserBlockServerResult.Blocked(host)
        return UserBlockServerResult.Failure(
            when (result.failure) {
                BlockServerFailure.NOT_LOGGED_IN -> "ログインしていません"
                BlockServerFailure.INVALID_SERVER -> "サーバーのホスト名を読み取れませんでした"
                BlockServerFailure.UNKNOWN__, null -> "ブロックできませんでした"
            },
        )
    }

    /**
     * @return 失敗したときに見せる文言。解除できたら null
     */
    suspend fun unblockServer(host: String): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(UnblockServerMutation(host = host)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        val result = response.data?.user?.unblockServer ?: return response.failureMessage()
        return when (result.failure) {
            null -> null
            UnblockServerFailure.NOT_LOGGED_IN -> "ログインしていません"
            UnblockServerFailure.UNKNOWN__ -> "ブロックを解除できませんでした"
        }
    }

    /**
     * @param postId [UserHomeTimelineResult.Post.reactionPostId] か [UserProfileResult.Post.reactionPostId]
     * @return 失敗したときに見せる文言。変えられたら null
     */
    suspend fun setLiked(postId: String, liked: Boolean): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(SetLikedMutation(postId = postId, liked = liked)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        val result = response.data?.user?.setLiked ?: return response.failureMessage()
        return when (result.failure) {
            null -> null

            SetLikedFailure.NOT_LOGGED_IN -> "ログインしていません"

            SetLikedFailure.POST_NOT_FOUND -> "この投稿にはいいねできません"

            SetLikedFailure.FEDERATION_DISABLED,
            SetLikedFailure.UNKNOWN__,
            -> "いいねを変更できませんでした"
        }
    }

    /**
     * @param postId [UserHomeTimelineResult.Post.reactionPostId] か [UserProfileResult.Post.reactionPostId]
     * @return 失敗したときに見せる文言。変えられたら null
     */
    suspend fun setBoosted(postId: String, boosted: Boolean): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(SetBoostedMutation(postId = postId, boosted = boosted)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        val result = response.data?.user?.setBoosted ?: return response.failureMessage()
        return when (result.failure) {
            null -> {
                boostChangesFlow.emit(BoostChange(reactionPostId = postId, boosted = boosted))
                null
            }

            SetBoostedFailure.NOT_LOGGED_IN -> "ログインしていません"

            SetBoostedFailure.POST_NOT_FOUND -> "この投稿はブーストできません"

            SetBoostedFailure.FEDERATION_DISABLED,
            SetBoostedFailure.UNKNOWN__,
            -> "ブーストを変更できませんでした"
        }
    }

    /**
     * @param noteId [UserProfileResult.Post.pinnableNoteId]
     * @return 失敗したときに見せる文言。変えられたら null
     */
    suspend fun setPinned(noteId: String, pinned: Boolean): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(SetPinnedMutation(noteId = noteId, pinned = pinned)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        val result = response.data?.user?.setPinned ?: return response.failureMessage()
        return when (result.failure) {
            null -> {
                pinChangesFlow.emit(PinChange(noteId = noteId, pinned = pinned))
                null
            }

            SetPinnedFailure.NOT_LOGGED_IN -> "ログインしていません"

            SetPinnedFailure.NOT_FOUND -> "投稿が見つかりません"

            SetPinnedFailure.TOO_MANY_PINS -> "ピン留めできる投稿は 5 件までです"

            SetPinnedFailure.UNKNOWN__ -> "ピン留めを変更できませんでした"
        }
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
                forgetFollowingGroups()
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
        forgetFollowingGroups()

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

    suspend fun adminAccess(): UserAdminAccessResult {
        val client = clients.client.value ?: return UserAdminAccessResult.Failure("接続先が未設定")
        val response = client.query(AdminAccessQuery()).execute()
        if (response.exception != null || response.hasErrors()) return UserAdminAccessResult.Failure(response.failureMessage())
        val data = response.data ?: return UserAdminAccessResult.Failure(response.failureMessage())
        val userInfo = data.userInfo ?: return UserAdminAccessResult.NotLoggedIn

        return if (userInfo.isAdmin) {
            UserAdminAccessResult.Granted
        } else {
            UserAdminAccessResult.Denied
        }
    }

    /**
     * 購読した時点の進み具合を流し、その後は変わるたびに流す。購読できないか切れたら [UserRemoteProfileRefreshResult.Failure] を流して終わる
     *
     * @param start 走っていなければ取り直しを始める。既に走っていれば、それの進み具合を流す
     */
    fun remoteProfileRefreshStream(start: Boolean): Flow<UserRemoteProfileRefreshResult> = flow {
        val client = clients.client.value
        if (client == null) {
            emit(UserRemoteProfileRefreshResult.Failure("接続先が未設定"))
            return@flow
        }
        val results = client.subscription(RemoteProfileRefreshSubscription(start = start)).toFlow()
            .map { response -> response.toRemoteProfileRefreshResult() }
            .transformWhile { result ->
                emit(result)
                result is UserRemoteProfileRefreshResult.Success
            }
        var lastResult: UserRemoteProfileRefreshResult? = null
        emitAll(results.onEach { lastResult = it })
        // エラーを流さずに終わったときも、もう届かないことを知らせる
        if (lastResult !is UserRemoteProfileRefreshResult.Failure) {
            emit(UserRemoteProfileRefreshResult.Failure("進み具合の受信が切れました"))
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
            displayNameEmojis = user.displayNameEmojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
            iconUrl = user.iconUrl,
            following = user.following,
            followingGroupId = user.followingGroupId,
            headerUrl = profile.headerUrl,
            biography = profile.biography,
            links = profile.links.map { UserProfileLink(name = it.name, url = it.url) },
            viewerIsOwner = profile.viewerIsOwner,
        )
    }

    /**
     * プロフィールの投稿。[userProfile] とは別に読むので、プロフィールは投稿を待たずに出せる
     *
     * @param after 最初のページなら null。続きは前回の [UserProfilePostsResult.Success.nextCursor]
     */
    suspend fun userProfilePosts(acct: String, after: String?): UserProfilePostsResult {
        val client = clients.awaitLoadedClient() ?: return UserProfilePostsResult.Failure("接続先が未設定")
        val response = client.query(UserProfilePostsQuery(acct = acct, after = Optional.present(after))).execute()
        if (response.exception != null || response.hasErrors()) return UserProfilePostsResult.Failure(response.failureMessage())
        val data = response.data ?: return UserProfilePostsResult.Failure(response.failureMessage())
        val page = data.userProfilePosts ?: return UserProfilePostsResult.Failure("投稿を読めません")
        return UserProfilePostsResult.Success(
            posts = page.posts.map { it.profilePostFields.toUserProfilePost() },
            nextCursor = page.nextCursor,
        )
    }

    /**
     * プロフィールのユーザーがいいねした投稿
     *
     * @param after 最初のページなら null。続きは前回の [UserProfileLikesResult.Success.nextCursor]
     */
    suspend fun userProfileLikes(acct: String, after: String?): UserProfileLikesResult {
        val client = clients.awaitLoadedClient() ?: return UserProfileLikesResult.Failure("接続先が未設定")
        val response = client.query(UserProfileLikesQuery(acct = acct, after = Optional.present(after))).execute()
        if (response.exception != null || response.hasErrors()) return UserProfileLikesResult.Failure(response.failureMessage())
        val data = response.data ?: return UserProfileLikesResult.Failure(response.failureMessage())
        val page = data.userProfileLikes ?: return UserProfileLikesResult.Hidden
        return UserProfileLikesResult.Success(
            posts = page.posts.map { it.timelinePostFields.toPost() },
            nextCursor = page.nextCursor,
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
                followingGroupId = user.followingGroupId,
                username = user.username,
                host = user.host,
                isLocal = user.isLocal,
                displayName = user.displayName,
                displayNameEmojis = user.displayNameEmojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
                iconUrl = user.iconUrl,
            ),
        )
    }

    /**
     * @param replyToPostId 返信する [UserHomeTimelineResult.Post.id]。投稿者へのメンションが本文の先頭に付く。返信でなければ null
     */
    suspend fun postNote(
        body: String,
        requestId: String,
        imageIds: List<String>,
        replyToPostId: String?,
    ): UserPostNoteResult {
        val client = clients.client.value ?: return UserPostNoteResult.Failure("接続先が未設定")
        val response = client.mutation(UserPostNoteMutation(body = body, requestId = requestId, imageIds = imageIds, replyToPostId = Optional.present(replyToPostId))).execute()
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
            UserPostNoteFailure.REPLY_TARGET_NOT_FOUND -> UserPostNoteResult.ReplyTargetNotFound
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
            null -> {
                deletedNoteIdsFlow.emit(noteId)
                null
            }

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
            null -> {
                val imageId = uploadNoteImage.imageId
                if (imageId == null) {
                    UserUploadNoteImageResult.Failure(response.failureMessage())
                } else {
                    UserUploadNoteImageResult.Success(imageId = imageId)
                }
            }

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
            summary = profile.summary,
            links = profile.links.map { UserProfileLink(name = it.name, url = it.url) },
            likesPublic = profile.likesPublic,
            followingCount = profile.followingCount,
            followersCount = profile.followersCount,
        )
    }

    /**
     * @param displayName 空なら未設定に戻す
     * @param summary 空なら未設定に戻す
     * @param links 並べる順。名前も URL も空の行はサーバーが捨てる
     * @param likesPublic いいねした投稿の一覧を公開するか
     */
    suspend fun updateProfile(
        displayName: String,
        summary: String,
        links: List<UserProfileLink>,
        likesPublic: Boolean,
    ): UserUpdateProfileResult {
        val client = clients.client.value ?: return UserUpdateProfileResult.Failure("接続先が未設定")
        val query =
            UpdateProfileQuery(
                displayName = Optional.present(displayName),
                summary = Optional.present(summary),
                links = Optional.present(links.map { ProfileLinkInput(name = it.name, url = it.url) }),
                likesPublic = Optional.present(likesPublic),
            )
        val response = client.mutation(UpdateProfileMutation(query)).execute()
        if (response.exception != null || response.hasErrors()) return UserUpdateProfileResult.Failure(response.failureMessage())
        val updateProfile = response.data?.user?.updateProfile ?: return UserUpdateProfileResult.Failure(response.failureMessage())

        return when (updateProfile.failure) {
            null -> UserUpdateProfileResult.Success
            UpdateProfileFailure.NOT_LOGGED_IN -> UserUpdateProfileResult.NotLoggedIn
            UpdateProfileFailure.TOO_LONG -> UserUpdateProfileResult.DisplayNameTooLong
            UpdateProfileFailure.SUMMARY_TOO_LONG -> UserUpdateProfileResult.SummaryTooLong
            UpdateProfileFailure.TOO_MANY_LINKS -> UserUpdateProfileResult.TooManyLinks
            UpdateProfileFailure.LINK_TOO_LONG -> UserUpdateProfileResult.LinkTooLong
            UpdateProfileFailure.INVALID_LINK -> UserUpdateProfileResult.InvalidLink
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

    /**
     * @param version 読み込みを始めてから変わったかを見分けるため、書き換えるたびに増やす
     */
    private data class FollowingGroupsState(
        val mainGroupName: String,
        val groups: List<UserFollowingGroup>,
        val version: Long,
    )

    /**
     * @param reactionPostId [UserHomeTimelineResult.Post.reactionPostId]
     */
    data class BoostChange(
        val reactionPostId: String,
        val boosted: Boolean,
    )

    /**
     * @param groupId 購読を入れたグループ。購読をやめたか、メインに入れたなら null
     */
    data class PinChange(
        val noteId: String,
        val pinned: Boolean,
    )

    data class FollowingChange(
        val actorUri: String,
        val following: Boolean,
        val groupId: String?,
    )

    private suspend fun FlowCollector<AccountStreamEvent>.collectAccountEvents(client: ApolloClient): Int {
        val receivedEventCount = client.subscription(AccountEventsSubscription()).toFlow()
            .mapNotNull { response -> response.data?.accountEvents }
            .onEach { event ->
                val arrivedPost = event.timelinePostArrived?.timelinePostFields?.toPost()
                val removedPosts = event.timelinePostsDeleted
                val arrivedNotification = event.notificationArrived?.notificationFields?.toNotification()
                if (arrivedPost != null) emit(AccountStreamEvent.PostArrived(arrivedPost))
                if (removedPosts != null) {
                    emit(
                        AccountStreamEvent.PostsRemoved(
                            PostRemovals(deletedPostIds = removedPosts.postIds.toSet(), boostUndonePostIds = removedPosts.boostUndonePostIds.toSet()),
                        ),
                    )
                }
                if (arrivedNotification != null) emit(AccountStreamEvent.NotificationArrived(arrivedNotification))
            }
            .count()
        // パスキーでのログインなど login・logout を通らずにセッションが変わることもあるので、購読が終わるたびに張り直させる
        client.closeSubscriptionConnection()
        return receivedEventCount
    }

    private fun ApolloResponse<RemoteProfileRefreshSubscription.Data>.toRemoteProfileRefreshResult(): UserRemoteProfileRefreshResult {
        val state = data?.remoteProfileRefresh
        if (exception != null || hasErrors() || state == null) return UserRemoteProfileRefreshResult.Failure(failureMessage())
        val progress = state.progress?.let {
            UserRemoteProfileRefreshResult.Progress(
                running = it.running,
                total = it.total,
                refreshed = it.refreshed,
                failed = it.failed,
                skipped = it.skipped,
                startedAt = it.startedAt,
                finishedAt = it.finishedAt,
            )
        }
        return UserRemoteProfileRefreshResult.Success(progress = progress)
    }

    private fun ActorListFields.append(page: ActorListFields): ActorListFields =
        copy(users = users + page.users, nextCursor = page.nextCursor)

    private fun ProfilePostFields.toUserProfilePost(): UserProfileResult.Post =
        UserProfileResult.Post(
            postId = id,
            deletableNoteId = deletableNoteId,
            pinned = pinned,
            pinnableNoteId = pinnableNoteId,
            reactionPostId = reactionPostId,
            body = UserPostBody(
                content = content,
                publishedAt = publishedAt,
                summary = summary,
                sensitive = sensitive,
                linkUrls = linkUrls,
                mentions = mentions.map { mention -> UserPostMention(start = mention.start, end = mention.end, acct = mention.acct) },
                images = images.map { image -> UserPostImage(url = image.url, thumbnailUrl = image.thumbnailUrl, description = image.description) },
                favouriteCount = favouriteCount,
                boostCount = boostCount,
                stamps = stamps.map { stamp -> UserPostStamp(emoji = stamp.emoji, imageUrl = stamp.imageUrl, count = stamp.count) },
                emojis = emojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
                shareUrl = url,
                likedByMe = likedByMe,
                boostedByMe = boostedByMe,
            ),
            boostedNoteAuthor = boostedNoteAuthor?.let { author ->
                UserProfileResult.Author(
                    username = author.username,
                    host = author.host,
                    displayName = author.displayName,
                    displayNameEmojis = author.displayNameEmojis.map { emoji ->
                        UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl)
                    },
                    iconUrl = author.iconUrl,
                )
            },
        )

    private companion object {
        const val TIMELINE_PAGE_SIZE = 40
        const val ACCOUNT_EVENT_MAX_CONSECUTIVE_FAILURES = 5

        /**
         * 読めるまでと読めなかったときに出す。サーバーの既定と合わせる
         */
        const val DEFAULT_MAIN_GROUP_NAME = "メイン"

        /**
         * 失敗の回数に応じて倍にしていく。5 回続けて失敗すると、諦めるまでに 5 + 10 + 20 + 40 秒待つ
         */
        val ACCOUNT_EVENT_RECONNECT_BASE_DELAY = 5.seconds

        /**
         * 投稿が来なくても、これだけ切れずにいられたなら接続できていたとみなし、失敗の回数を数え直す
         */
        val ACCOUNT_EVENT_HEALTHY_CONNECTION_DURATION = 1.minutes
    }
}
