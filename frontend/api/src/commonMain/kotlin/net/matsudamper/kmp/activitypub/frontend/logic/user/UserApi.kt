package net.matsudamper.kmp.activitypub.frontend.logic.user

import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
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
import com.apollographql.apollo.api.Query
import com.apollographql.cache.normalized.FetchPolicy
import com.apollographql.cache.normalized.apolloStore
import com.apollographql.cache.normalized.doNotStore
import com.apollographql.cache.normalized.fetchPolicy
import com.apollographql.cache.normalized.watch
import net.matsudamper.kmp.activitypub.frontend.graphql.AccountEventsSubscription
import net.matsudamper.kmp.activitypub.frontend.graphql.AdminAccessQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.BlockAccountMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.BlockServerMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.BlockedAccountsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.BlockedServersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.CreateFollowingGroupMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.CustomEmojisQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.DeleteCustomEmojiMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.DeleteNoteMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.EditNoteMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.EditableNoteBodyQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FailedDeliveriesQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FailedDeliveryQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowerUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowingGroupsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.FollowingUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenResolveUserQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenSearchPostsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenSearchUsersQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeTimelineLinkPreviewsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.LoginGateQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.MyProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.NotificationsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.PostCountsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.PostDetailScreenQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.PostRepliesQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.PostRevisionsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.QuoteTargetQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.RemoteProfileRefreshSubscription
import net.matsudamper.kmp.activitypub.frontend.graphql.RenameFollowingGroupMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.ReplyTargetQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.SetBoostedMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.SetFollowingMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.SetLikedMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.SetPinnedMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.SetReactionMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.TimelineQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.UnblockAccountMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UnblockServerMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UpdateProfileHeaderMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UpdateProfileIconMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UpdateProfileMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UploadCustomEmojiMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UploadNoteImageMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLoginMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserLogoutMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserPostNoteMutation
import net.matsudamper.kmp.activitypub.frontend.graphql.UserProfileLikesQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.UserProfilePostsQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.UserProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.ActorListFields
import net.matsudamper.kmp.activitypub.frontend.graphql.fragment.ProfilePostFields
import net.matsudamper.kmp.activitypub.frontend.graphql.type.BlockAccountFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.BlockServerFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.CreateFollowingGroupFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.DeleteCustomEmojiFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.DeleteNoteFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.EditNoteFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.PostNotePollInput
import net.matsudamper.kmp.activitypub.frontend.graphql.type.ProfileLinkInput
import net.matsudamper.kmp.activitypub.frontend.graphql.type.ReactionInput
import net.matsudamper.kmp.activitypub.frontend.graphql.type.RenameFollowingGroupFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.SetBoostedFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.SetLikedFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.SetPinnedFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.SetReactionFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UnblockAccountFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UnblockServerFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UpdateProfileFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UpdateProfileHeaderFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UpdateProfileIconFailure
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UpdateProfileQuery
import net.matsudamper.kmp.activitypub.frontend.graphql.type.UploadCustomEmojiFailure
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
    private val editedNoteIdsFlow = MutableSharedFlow<String>()

    /**
     * [editNote] で編集できた投稿の id。どの画面で編集しても、投稿を出している画面がここで読み直す
     */
    val editedNoteIds = editedNoteIdsFlow.asSharedFlow()
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

    /**
     * [userProfile] に渡して本人のプロフィールが返った username。同じアカウントでも `name` と `name@host` でキャッシュが分かれるので、
     * 自分のプロフィールを変えたときに書き換える先を覚えておく
     */
    private val ownUserProfileUsernames = MutableStateFlow(setOf<String>())
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
        return UserLinkPreviewsResult.Success(
            previews = data.linkPreviews.map {
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
        val post = data.timelinePost ?: return UserPostDetailResult.NotFound
        return UserPostDetailResult.Found(post.timelinePostFields.toPost())
    }

    /**
     * 他のサーバーの投稿は相手のサーバーから数を取り直すので遅い。個別投稿画面を開いたときに使う
     *
     * @param postId [UserHomeTimelineResult.Post.id]
     * @return 取れなければ null
     */
    suspend fun postCounts(postId: String): UserPostCounts? {
        val client = clients.awaitLoadedClient() ?: return null
        val response = client.query(PostCountsQuery(postId = postId)).execute()
        if (response.exception != null || response.hasErrors()) return null
        val counts = response.data?.postCounts ?: return null
        return UserPostCounts(replyCount = counts.replyCount, favouriteCount = counts.favouriteCount, boostCount = counts.boostCount)
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
        val post = data.replyTarget ?: return UserPostDetailResult.NotFound
        return UserPostDetailResult.Found(post.timelinePostFields.toPost())
    }

    /**
     * 投稿に付いた返信。サーバーが保存しているものだけ
     *
     * @param postId [UserHomeTimelineResult.Post.id]
     */
    suspend fun replies(postId: String): UserPostRepliesResult {
        val client = clients.awaitLoadedClient() ?: return UserPostRepliesResult.Failure("接続先が未設定")
        val response = client.query(PostRepliesQuery(postId = postId)).execute()
        if (response.exception != null || response.hasErrors()) return UserPostRepliesResult.Failure(response.failureMessage())
        val data = response.data ?: return UserPostRepliesResult.Failure(response.failureMessage())
        return UserPostRepliesResult.Success(data.replies.map { it.timelinePostFields.toPost() })
    }

    /**
     * 投稿の版を新しい順に。最初が今の版
     *
     * @param postId [UserHomeTimelineResult.Post.id]
     */
    fun postRevisionsPaging(postId: String): CursorPagingModel<PostRevisionsQuery.Data> {
        return CursorPagingModel(
            clients = clients,
            firstQuery = PostRevisionsQuery(postId = postId, after = Optional.present(null), limit = POST_REVISIONS_PAGE_SIZE),
            queryAfter = { cursor -> PostRevisionsQuery(postId = postId, after = Optional.present(cursor), limit = POST_REVISIONS_PAGE_SIZE) },
            nextCursorOf = { it.userInfo?.postRevisions?.nextCursor },
            appendPage = { before, page -> before.withNextPage(page) },
        )
    }

    /**
     * 引用先の投稿
     *
     * @param postId [UserHomeTimelineResult.Post.id]
     * @return 引用でないか、引用先を読めなければ [UserPostDetailResult.NotFound]
     */
    suspend fun quoteTarget(postId: String): UserPostDetailResult {
        val client = clients.awaitLoadedClient() ?: return UserPostDetailResult.Failure("接続先が未設定")
        val response = client.query(QuoteTargetQuery(postId = postId)).execute()
        if (response.exception != null || response.hasErrors()) return UserPostDetailResult.Failure(response.failureMessage())
        val data = response.data ?: return UserPostDetailResult.Failure(response.failureMessage())
        val post = data.quoteTarget ?: return UserPostDetailResult.NotFound
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

    suspend fun blockedAccounts(): UserBlockedAccountsResult {
        val client = clients.client.value ?: return UserBlockedAccountsResult.Failure("接続先が未設定")
        val response = client.query(BlockedAccountsQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()
        if (response.exception != null || response.hasErrors()) return UserBlockedAccountsResult.Failure(response.failureMessage())
        val userInfo = response.data?.userInfo ?: return UserBlockedAccountsResult.Failure("ログインしていません")
        return UserBlockedAccountsResult.Success(userInfo.blockedAccounts.map { UserBlockedAccount(actorUri = it.actorUri, acct = it.acct) })
    }

    /**
     * @param acct プロフィールを開くときと同じ username@host
     */
    suspend fun blockAccount(acct: String): UserBlockAccountResult {
        val client = clients.client.value ?: return UserBlockAccountResult.Failure("接続先が未設定")
        val response = client.mutation(BlockAccountMutation(acct = acct)).execute()
        if (response.exception != null || response.hasErrors()) return UserBlockAccountResult.Failure(response.failureMessage())
        val result = response.data?.user?.blockAccount ?: return UserBlockAccountResult.Failure(response.failureMessage())
        val account = result.account
        if (account != null) return UserBlockAccountResult.Blocked(UserBlockedAccount(actorUri = account.actorUri, acct = account.acct))
        return UserBlockAccountResult.Failure(
            when (result.failure) {
                BlockAccountFailure.NOT_LOGGED_IN -> "ログインしていません"
                BlockAccountFailure.NOT_FOUND -> "アカウントが見つかりませんでした"
                BlockAccountFailure.LOCAL_ACCOUNT -> "このサーバーのアカウントはブロックできません"
                BlockAccountFailure.UNKNOWN__, null -> "ブロックできませんでした"
            },
        )
    }

    /**
     * @return 失敗したときに見せる文言。解除できたら null
     */
    suspend fun unblockAccount(actorUri: String): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(UnblockAccountMutation(actorUri = actorUri)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        val result = response.data?.user?.unblockAccount ?: return response.failureMessage()
        return when (result.failure) {
            null -> null
            UnblockAccountFailure.NOT_LOGGED_IN -> "ログインしていません"
            UnblockAccountFailure.UNKNOWN__ -> "ブロックを解除できませんでした"
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
     * 投稿に付けるいいねかリアクションを [reaction] に置き換える。1 つの投稿に付けられるのはどちらか 1 つ
     *
     * @param postId [UserHomeTimelineResult.Post.reactionPostId] か [UserProfileResult.Post.reactionPostId]
     * @param reaction null なら外す
     * @return 失敗したときに見せる文言。変えられたら null
     */
    suspend fun setReaction(postId: String, reaction: UserReaction?): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val input = when (reaction) {
            null -> Optional.absent()
            is UserReaction.Unicode -> Optional.present(ReactionInput(unicode = Optional.present(reaction.emoji)))
            is UserReaction.Custom -> Optional.present(ReactionInput(customEmojiShortcode = Optional.present(reaction.shortcode)))
        }
        val response = client.mutation(SetReactionMutation(postId = postId, reaction = input)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        val result = response.data?.user?.setReaction ?: return response.failureMessage()
        return when (result.failure) {
            null -> null

            SetReactionFailure.NOT_LOGGED_IN -> "ログインしていません"

            SetReactionFailure.POST_NOT_FOUND -> "この投稿にはリアクションできません"

            SetReactionFailure.INVALID_EMOJI -> "この絵文字は使えません"

            SetReactionFailure.FEDERATION_DISABLED,
            SetReactionFailure.UNKNOWN__,
            -> "リアクションを変更できませんでした"
        }
    }

    suspend fun customEmojis(): UserCustomEmojisResult {
        val client = clients.client.value ?: return UserCustomEmojisResult.Failure("接続先が未設定")
        val response = client.query(CustomEmojisQuery()).fetchPolicy(FetchPolicy.NetworkOnly).execute()
        if (response.exception != null || response.hasErrors()) return UserCustomEmojisResult.Failure(response.failureMessage())
        val emojis = response.data?.customEmojis ?: return UserCustomEmojisResult.Failure("カスタム絵文字を使えません")
        return UserCustomEmojisResult.Success(
            emojis.map { UserLocalCustomEmoji(id = it.id, shortcode = it.shortcode, imageUrl = it.imageUrl, deletable = it.deletable) },
        )
    }

    /**
     * @param shortcode コロンを含めない名前
     * @param image PNG / JPEG / GIF / WebP。形式はサーバーが判定する
     */
    suspend fun uploadCustomEmoji(shortcode: String, image: ByteArray): UserUploadCustomEmojiResult {
        val client = clients.client.value ?: return UserUploadCustomEmojiResult.Failure("接続先が未設定")
        val upload =
            DefaultUpload.Builder()
                .content(image)
                .contentType("application/octet-stream")
                .fileName("image")
                .build()
        // multipart はプリフライト無しで別オリジンから送れるので、サーバーはこのヘッダーで弾いている
        val response = client.mutation(UploadCustomEmojiMutation(shortcode = shortcode, image = upload)).addHttpHeader("Apollo-Require-Preflight", "true").execute()
        if (response.exception != null || response.hasErrors()) return UserUploadCustomEmojiResult.Failure(response.failureMessage())
        val result = response.data?.user?.uploadCustomEmoji ?: return UserUploadCustomEmojiResult.Failure(response.failureMessage())
        val emoji = result.emoji
        if (emoji != null) {
            return UserUploadCustomEmojiResult.Success(
                UserLocalCustomEmoji(id = emoji.id, shortcode = emoji.shortcode, imageUrl = emoji.imageUrl, deletable = emoji.deletable),
            )
        }
        return UserUploadCustomEmojiResult.Failure(
            when (result.failure) {
                UploadCustomEmojiFailure.NOT_LOGGED_IN -> "ログインしていません"

                UploadCustomEmojiFailure.INVALID_SHORTCODE -> "名前は英数字と _ で 64 文字までにしてください"

                UploadCustomEmojiFailure.DUPLICATE_SHORTCODE -> "同じ名前の絵文字があります"

                UploadCustomEmojiFailure.TOO_LARGE -> "画像は 1MB までにしてください"

                UploadCustomEmojiFailure.UNSUPPORTED_IMAGE -> "PNG / JPEG / GIF / WebP の画像を選んでください"

                UploadCustomEmojiFailure.UNSUPPORTED_OPERATION -> "このサーバーでは絵文字を置けません"

                UploadCustomEmojiFailure.INTERNAL_SERVER_ERROR,
                UploadCustomEmojiFailure.UNKNOWN__,
                null,
                -> "絵文字を追加できませんでした"
            },
        )
    }

    /**
     * @return 失敗したときに見せる文言。消せたら null
     */
    suspend fun deleteCustomEmoji(id: String): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(DeleteCustomEmojiMutation(id = id)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        val result = response.data?.user?.deleteCustomEmoji ?: return response.failureMessage()
        return when (result.failure) {
            null -> null

            DeleteCustomEmojiFailure.NOT_LOGGED_IN -> "ログインしていません"

            DeleteCustomEmojiFailure.NOT_FOUND -> "この絵文字は消せません"

            DeleteCustomEmojiFailure.UNSUPPORTED_OPERATION,
            DeleteCustomEmojiFailure.UNKNOWN__,
            -> "絵文字を削除できませんでした"
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
                ownUserProfileUsernames.value = setOf()
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
        ownUserProfileUsernames.value = setOf()
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
     * 送るのを諦めた配信の一覧。新しく積んだ順
     */
    fun failedDeliveriesPaging(): CursorPagingModel<FailedDeliveriesQuery.Data> {
        return CursorPagingModel(
            clients = clients,
            firstQuery = FailedDeliveriesQuery(after = Optional.present(null), limit = FAILED_DELIVERIES_PAGE_SIZE),
            queryAfter = { cursor -> FailedDeliveriesQuery(after = Optional.present(cursor), limit = FAILED_DELIVERIES_PAGE_SIZE) },
            nextCursorOf = { it.userInfo?.failedDeliveries?.nextCursor },
            appendPage = { before, page -> before.withNextPage(page) },
        )
    }

    /**
     * @param id [UserFailedDelivery.id]
     */
    suspend fun failedDelivery(id: String): UserFailedDeliveryResult {
        val client = clients.awaitLoadedClient() ?: return UserFailedDeliveryResult.Failure("接続先が未設定")
        val response = client.query(FailedDeliveryQuery(id = id)).fetchPolicy(FetchPolicy.NetworkOnly).execute()
        if (response.exception != null || response.hasErrors()) return UserFailedDeliveryResult.Failure(response.failureMessage())
        val data = response.data ?: return UserFailedDeliveryResult.Failure(response.failureMessage())
        val userInfo = data.userInfo ?: return UserFailedDeliveryResult.NotLoggedIn
        val delivery = userInfo.failedDelivery ?: return UserFailedDeliveryResult.NotFound
        return UserFailedDeliveryResult.Found(delivery.failedDeliveryFields.toUserFailedDelivery())
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
        if (profile.viewerIsOwner) ownUserProfileUsernames.update { it + username }

        return profile.toFound()
    }

    /**
     * [userProfile] で読んだ内容がキャッシュで書き換わるたびに流す。自分のプロフィールを変えると、変えた後の内容が流れる。
     * [userProfile] でまだ読めていない間は何も流さない
     */
    fun watchUserProfile(username: String): Flow<UserProfileResult.Found> =
        watchCache(UserProfileQuery(username = username)).mapNotNull { it.userProfile?.toFound() }

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

    /**
     * 本文で投稿を探す
     *
     * @param after 最初のページなら null。続きは前回の [UserPostSearchResult.Success.nextCursor]
     */
    suspend fun searchPosts(query: String, after: String?): UserPostSearchResult {
        val client = clients.awaitLoadedClient() ?: return UserPostSearchResult.Failure("接続先が未設定")
        val response = client.query(HomeScreenSearchPostsQuery(query = query, after = Optional.present(after))).execute()
        if (response.exception != null || response.hasErrors()) return UserPostSearchResult.Failure(response.failureMessage())
        val data = response.data ?: return UserPostSearchResult.Failure(response.failureMessage())
        val page = data.searchPosts ?: return UserPostSearchResult.Failure("ログインしていません")
        return UserPostSearchResult.Success(
            posts = page.posts.map { it.timelinePostFields.toPost() },
            nextCursor = page.nextCursor,
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
     * @param quotePostId 引用する [UserHomeTimelineResult.Post.id]。引用でなければ null
     * @param contentWarning 閲覧注意の注意書き。閲覧注意にしなければ null
     * @param mentionAcct 宛てるユーザーの `username@host`。返信先の投稿者のほかに宛てなければ null
     * @param sensitive 閲覧注意の投稿にするか
     */
    suspend fun postNote(
        body: String,
        requestId: String,
        contentWarning: String?,
        imageIds: List<String>,
        replyToPostId: String?,
        quotePostId: String?,
        mentionAcct: String?,
        sensitive: Boolean,
        poll: UserPostNotePoll?,
    ): UserPostNoteResult {
        val client = clients.client.value ?: return UserPostNoteResult.Failure("接続先が未設定")
        val mutation =
            UserPostNoteMutation(
                body = body,
                requestId = requestId,
                contentWarning = Optional.present(contentWarning),
                imageIds = imageIds,
                replyToPostId = Optional.present(replyToPostId),
                quotePostId = Optional.present(quotePostId),
                mentionAcct = Optional.present(mentionAcct),
                sensitive = sensitive,
                poll = Optional.present(
                    poll?.let {
                        PostNotePollInput(options = it.options, multipleChoice = it.multipleChoice, expiresInSeconds = it.expiresInSeconds)
                    },
                ),
            )
        val response = client.mutation(mutation).execute()
        if (response.exception != null || response.hasErrors()) return UserPostNoteResult.Failure(response.failureMessage())
        val postNote = response.data?.user?.postNote ?: return UserPostNoteResult.Failure(response.failureMessage())

        return when (postNote.failure) {
            null -> UserPostNoteResult.Success
            UserPostNoteFailure.NOT_LOGGED_IN -> UserPostNoteResult.NotLoggedIn
            UserPostNoteFailure.EMPTY_BODY -> UserPostNoteResult.EmptyBody
            UserPostNoteFailure.TOO_LONG -> UserPostNoteResult.TooLong
            UserPostNoteFailure.CONTENT_WARNING_TOO_LONG -> UserPostNoteResult.ContentWarningTooLong
            UserPostNoteFailure.FEDERATION_DISABLED -> UserPostNoteResult.FederationDisabled
            UserPostNoteFailure.TOO_MANY_IMAGES -> UserPostNoteResult.TooManyImages
            UserPostNoteFailure.INVALID_POLL -> UserPostNoteResult.InvalidPoll
            UserPostNoteFailure.IMAGE_NOT_FOUND -> UserPostNoteResult.ImageNotFound
            UserPostNoteFailure.REPLY_TARGET_NOT_FOUND -> UserPostNoteResult.ReplyTargetNotFound
            UserPostNoteFailure.QUOTE_TARGET_NOT_FOUND -> UserPostNoteResult.QuoteTargetNotFound
            UserPostNoteFailure.MENTION_TARGET_NOT_FOUND -> UserPostNoteResult.MentionTargetNotFound
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

    /**
     * @param noteId [UserHomeTimelineResult.Post.deletableNoteId]
     */
    suspend fun editableNoteBody(noteId: String): UserEditableNoteBodyResult {
        val client = clients.client.value ?: return UserEditableNoteBodyResult.Failure("接続先が未設定")
        val response = client.query(EditableNoteBodyQuery(noteId = noteId)).execute()
        if (response.exception != null || response.hasErrors()) return UserEditableNoteBodyResult.Failure(response.failureMessage())
        val userInfo = response.data?.userInfo ?: return UserEditableNoteBodyResult.Failure("ログインしていません")
        val body = userInfo.editableNoteBody ?: return UserEditableNoteBodyResult.Failure("投稿が見つかりません")
        return UserEditableNoteBodyResult.Success(body)
    }

    /**
     * @param noteId [UserHomeTimelineResult.Post.deletableNoteId]
     * @return 編集できなければ、その理由
     */
    suspend fun editNote(noteId: String, body: String): String? {
        val client = clients.client.value ?: return "接続先が未設定"
        val response = client.mutation(EditNoteMutation(noteId = noteId, body = body)).execute()
        if (response.exception != null || response.hasErrors()) return response.failureMessage()
        val editNote = response.data?.user?.editNote ?: return response.failureMessage()
        return when (editNote.failure) {
            null -> {
                editedNoteIdsFlow.emit(noteId)
                null
            }

            EditNoteFailure.NOT_LOGGED_IN -> "ログインしていません"

            EditNoteFailure.NOT_FOUND -> "投稿が見つかりません"

            EditNoteFailure.EMPTY_BODY -> "本文を入力してください"

            EditNoteFailure.TOO_LONG -> "本文が長すぎます"

            EditNoteFailure.FEDERATION_DISABLED -> "このサーバーでは投稿を編集できません"

            EditNoteFailure.UNKNOWN__ -> "Unknown"
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

        return profile.toFound()
    }

    /**
     * [myProfile] で読んだ内容がキャッシュで書き換わるたびに流す。プロフィールを変えると、変えた後の内容が流れる。
     * [myProfile] でまだ読めていない間は何も流さない
     */
    fun watchMyProfile(): Flow<UserMyProfileResult.Found> =
        watchCache(MyProfileQuery()).mapNotNull { it.profile?.toFound() }

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

        // サーバーが空の行を捨てたり空文字を未設定に直したりするので、送った値ではなく読み直した値をキャッシュに入れる
        if (updateProfile.failure == null) refetchOwnProfiles(client)

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

        val iconUrl = updateProfileIcon.iconUrl
        if (updateProfileIcon.failure == null && iconUrl != null) {
            client.updateCachedOwnProfiles(
                myProfile = { it.copy(iconUrl = iconUrl) },
                userProfile = { it.copy(user = it.user.copy(iconUrl = iconUrl)) },
            )
        }

        return when (updateProfileIcon.failure) {
            null -> UserUpdateProfileIconResult.Success(iconUrl = iconUrl)
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
     * @param header 画像ファイルの中身
     */
    suspend fun updateProfileHeader(header: ByteArray): UserUpdateProfileHeaderResult {
        val client = clients.client.value ?: return UserUpdateProfileHeaderResult.Failure("接続先が未設定")
        val response = client.mutation(UpdateProfileHeaderMutation(header = Base64.Default.encode(header))).execute()
        if (response.exception != null || response.hasErrors()) return UserUpdateProfileHeaderResult.Failure(response.failureMessage())
        val updateProfileHeader = response.data?.user?.updateProfileHeader ?: return UserUpdateProfileHeaderResult.Failure(response.failureMessage())

        val headerUrl = updateProfileHeader.headerUrl
        if (updateProfileHeader.failure == null && headerUrl != null) {
            client.updateCachedOwnProfiles(
                myProfile = { it.copy(headerUrl = headerUrl) },
                userProfile = { it.copy(headerUrl = headerUrl) },
            )
        }

        return when (updateProfileHeader.failure) {
            null -> UserUpdateProfileHeaderResult.Success(headerUrl = headerUrl)
            UpdateProfileHeaderFailure.NOT_LOGGED_IN -> UserUpdateProfileHeaderResult.NotLoggedIn
            UpdateProfileHeaderFailure.TOO_LARGE -> UserUpdateProfileHeaderResult.TooLarge
            UpdateProfileHeaderFailure.DIMENSIONS_TOO_LARGE -> UserUpdateProfileHeaderResult.DimensionsTooLarge
            UpdateProfileHeaderFailure.UNSUPPORTED_IMAGE -> UserUpdateProfileHeaderResult.UnsupportedImage
            UpdateProfileHeaderFailure.UNSUPPORTED_OPERATION -> UserUpdateProfileHeaderResult.UnsupportedOperation
            UpdateProfileHeaderFailure.INTERNAL_SERVER_ERROR -> UserUpdateProfileHeaderResult.InternalServerError
            UpdateProfileHeaderFailure.FEDERATION_DISABLED -> UserUpdateProfileHeaderResult.FederationDisabled
            UpdateProfileHeaderFailure.UNKNOWN__ -> UserUpdateProfileHeaderResult.Failure("Unknown")
        }
    }

    /**
     * キャッシュがまだ無い間は何も流さない
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun <D : Query.Data> watchCache(query: Query<D>): Flow<D> =
        clients.client
            .flatMapLatest { client ->
                client?.query(query)
                    ?.fetchPolicy(FetchPolicy.CacheOnly)
                    ?.watch()
                    ?: emptyFlow()
            }
            .filter { it.exception == null && it.errors.isNullOrEmpty() }
            .mapNotNull { it.data }

    /**
     * 変更の戻り値で分かった内容を、取得し直さずに自分のプロフィールのキャッシュへ書く。キャッシュが無いものは何もしない
     */
    private suspend fun ApolloClient.updateCachedOwnProfiles(
        myProfile: (MyProfileQuery.Profile) -> MyProfileQuery.Profile,
        userProfile: (UserProfileQuery.UserProfile) -> UserProfileQuery.UserProfile,
    ) {
        updateCache(MyProfileQuery()) { data -> data.copy(profile = data.profile?.let(myProfile)) }
        ownUserProfileUsernames.value.forEach { username ->
            updateCache(UserProfileQuery(username = username)) { data -> data.copy(userProfile = data.userProfile?.let(userProfile)) }
        }
    }

    private suspend fun <D : Query.Data> ApolloClient.updateCache(query: Query<D>, transform: (D) -> D) {
        val cached = apolloStore.readOperation(query)
        if (!cached.errors.isNullOrEmpty()) return
        val data = cached.data ?: return
        apolloStore.writeOperation(operation = query, data = transform(data), publish = true)
    }

    /**
     * 読めなくても、変更そのものは通っているので失敗にしない。キャッシュは古いままになる
     */
    private suspend fun refetchOwnProfiles(client: ApolloClient) {
        client.query(MyProfileQuery()).execute()
        ownUserProfileUsernames.value.forEach { username ->
            client.query(UserProfileQuery(username = username)).execute()
        }
    }

    private fun MyProfileQuery.Profile.toFound(): UserMyProfileResult.Found =
        UserMyProfileResult.Found(
            username = username,
            displayName = displayName,
            iconUrl = iconUrl,
            headerUrl = headerUrl,
            summary = summary,
            links = links.map { UserProfileLink(name = it.name, url = it.url) },
            likesPublic = likesPublic,
            followingCount = followingCount,
            followersCount = followersCount,
        )

    private fun UserProfileQuery.UserProfile.toFound(): UserProfileResult.Found =
        UserProfileResult.Found(
            actorUri = user.actorUri,
            username = user.username,
            host = user.host,
            displayName = user.displayName,
            displayNameEmojis = user.displayNameEmojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
            iconUrl = user.iconUrl,
            following = user.following,
            followingGroupId = user.followingGroupId,
            headerUrl = headerUrl,
            biography = biography,
            links = links.map { UserProfileLink(name = it.name, url = it.url) },
            viewerIsOwner = viewerIsOwner,
        )

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
                val arrivedNotification = event.notificationGroupUpdated?.notificationFields?.toNotification()
                if (arrivedPost != null) emit(AccountStreamEvent.PostArrived(arrivedPost))
                if (removedPosts != null) {
                    emit(
                        AccountStreamEvent.PostsRemoved(
                            PostRemovals(deletedPostIds = removedPosts.postIds.toSet(), boostUndonePostIds = removedPosts.boostUndonePostIds.toSet()),
                        ),
                    )
                }
                if (arrivedNotification != null) emit(AccountStreamEvent.NotificationArrived(arrivedNotification))
                val updatedPost = event.timelinePostUpdated?.timelinePostFields?.toPost()
                if (updatedPost != null) emit(AccountStreamEvent.PostUpdated(updatedPost))
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
                language = language,
                publishedAt = publishedAt,
                summary = summary,
                sensitive = sensitive,
                linkUrls = linkUrls,
                linkObjectUrl = linkObjectUrl,
                mentions = mentions.map { mention -> UserPostMention(start = mention.start, end = mention.end, acct = mention.acct) },
                images = images.map { image -> UserPostImage(url = image.url, thumbnailUrl = image.thumbnailUrl, description = image.description) },
                videos = videos.map { video -> UserPostVideo(url = video.url, description = video.description) },
                favouriteCount = favouriteCount,
                boostCount = boostCount,
                replyCount = replyCount,
                stampCountsReceived = stampCountsReceived,
                stamps = stamps.map { stamp -> UserPostStamp(emoji = stamp.emoji, imageUrl = stamp.imageUrl, count = stamp.count, customEmojiShortcode = stamp.customEmojiShortcode) },
                emojis = emojis.map { emoji -> UserCustomEmoji(name = emoji.name, imageUrl = emoji.imageUrl) },
                shareUrl = url,
                likedByMe = likedByMe,
                viewerReaction = viewerReaction?.let { UserViewerReaction(emoji = it.emoji, imageUrl = it.imageUrl) },
                boostedByMe = boostedByMe,
                edited = edited,
                poll = poll?.let { poll ->
                    UserPostPoll(
                        options = poll.options.map { option -> UserPostPollOption(title = option.title, votesCount = option.votesCount) },
                        multipleChoice = poll.multipleChoice,
                        endsAt = poll.endsAt,
                        expired = poll.expired,
                        votersCount = poll.votersCount,
                    )
                },
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
            hasQuoteTarget = hasQuoteTarget,
        )

    private companion object {
        const val TIMELINE_PAGE_SIZE = 40
        const val POST_REVISIONS_PAGE_SIZE = 20
        const val FAILED_DELIVERIES_PAGE_SIZE = 30
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
