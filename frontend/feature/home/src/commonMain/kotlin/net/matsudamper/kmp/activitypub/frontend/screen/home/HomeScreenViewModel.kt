package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.graphql.HomeScreenSearchUsersQuery
import net.matsudamper.kmp.activitypub.frontend.logic.CursorPagingModel
import net.matsudamper.kmp.activitypub.frontend.logic.UpdateOperationResponseResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.HomeTimelineStreamEvent
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserMyProfileResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserProfileResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserResolveResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserSearchResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.toUserSearchResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.withArrivedPosts
import net.matsudamper.kmp.activitypub.frontend.logic.user.withFollowing
import net.matsudamper.kmp.activitypub.frontend.logic.user.withoutNote
import net.matsudamper.kmp.activitypub.frontend.screen.createPostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class HomeScreenViewModel(
    @InjectedParam private val selectedTab: HomeScreenUiState.Tab,
    private val userApi: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val searchStateFlow: MutableStateFlow<SearchState> = MutableStateFlow(SearchState.NotSearched)
    private var searchJob: Job? = null
    private var searchWatchJob: Job? = null
    private var searchPaging: CursorPagingModel<HomeScreenSearchUsersQuery.Data>? = null
    private var profileJob: Job? = null
    private var timelineJob: Job? = null
    private val timelinePaging = userApi.homeTimelinePaging()
    private var timelineNextCursor: String? = null
    private var timelinePosts = listOf<UserHomeTimelineResult.Post>()
    private var linkPreviewRequestedPostIds = setOf<String>()
    private var linkPreviews = mapOf<String, List<UserLinkPreviewsResult.LinkPreview>>()
    private var timelineArrivalsJob: Job? = null
    private var myProfileWithPosts: UserProfileResult.Found? = null
    private var myUsername: String? = null
    private var myPostsRetryJob: Job? = null

    /**
     * 自分で切ったときは、画面に戻ってきてもつなぎ直さない
     */
    private var timelineStreamStoppedByUser = false

    /**
     * 読み直しの結果は要求した時点のものなので、その間に流れてきた投稿を先頭に足し直す
     */
    private var postsArrivedSinceRefresh = listOf<UserHomeTimelineResult.Post>()
    private var followingChangeInProgress = false
    private var timelineStreamConnectionStatus = HomeScreenUiState.TimelineStreamStatus.Receiving

    /**
     * 再接続直後は購読がつながっていても切れていた間の投稿を読み直し終えるまでは追いついていないので、再接続中として見せる
     */
    private var timelineCatchingUpAfterReconnect = false
    private var sensitiveImagesBlurred = true

    /**
     * 一括で切り替えたら、画像ごとに開け閉めした分は捨てて揃える
     */
    private var blurToggledImageUrls = setOf<String>()

    /**
     * 確かめのダイアログを出している投稿。出していなければ null
     */
    private var deletingNoteId: String? = null
    private var noteDeletionInProgress = false

    private val mutableUiStateFlow: MutableStateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                selectedTab = selectedTab,
                timelineItems = listOf(),
                timelineRefreshing = false,
                timelineFooter = HomeScreenUiState.TimelineFooter.None,
                timelineStreamStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
                sensitiveImagesBlurred = sensitiveImagesBlurred,
                search = HomeScreenUiState.Search(
                    query = "",
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userPosts = HomeScreenUiState.UserPosts.Loading,
                userTabIconUrl = null,
                deleteNoteDialog = null,
                listener = object : HomeScreenUiState.Listener {
                    override fun onClickPost() {
                        viewModelScope.launch {
                            events.send { it.navigateToPost() }
                        }
                    }

                    override fun onClickSettings() {
                        viewModelScope.launch {
                            events.send { it.navigateToSettings() }
                        }
                    }

                    override fun onClickTab(tab: HomeScreenUiState.Tab) {
                        val reselected = mutableUiStateFlow.value.selectedTab == tab
                        viewModelScope.launch {
                            if (reselected) {
                                events.send { it.scrollTabToTop(tab) }
                            } else {
                                events.send { it.navigateToTab(tab) }
                            }
                        }
                    }

                    override fun onTabChanged(tab: HomeScreenUiState.Tab) {
                        mutableUiStateFlow.update { it.copy(selectedTab = tab) }
                    }

                    override fun onRefreshTimeline() {
                        loadTimeline(TimelineLoadTrigger.PullToRefresh)
                    }

                    override fun onScrolledToTimelineEnd() {
                        if (mutableUiStateFlow.value.timelineFooter is HomeScreenUiState.TimelineFooter.Error) return
                        loadTimeline(TimelineLoadTrigger.Append)
                    }

                    override fun onClickRetryTimelineLoadMore() {
                        loadTimeline(TimelineLoadTrigger.Append)
                    }

                    override fun onClickTimelineStreamStatus() {
                        when (mutableUiStateFlow.value.timelineStreamStatus) {
                            HomeScreenUiState.TimelineStreamStatus.Receiving,
                            HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect,
                            -> stopTimelineArrivals()

                            HomeScreenUiState.TimelineStreamStatus.Disconnected -> {
                                timelineStreamStoppedByUser = false
                                collectTimelineArrivals()
                                // 切れていた間の投稿は流れてこないので、読み直して埋める
                                loadTimeline(TimelineLoadTrigger.Reconnect)
                            }
                        }
                    }

                    override fun onClickSensitiveImagesVisibility() {
                        sensitiveImagesBlurred = !sensitiveImagesBlurred
                        blurToggledImageUrls = setOf()
                        val myProfile = myProfileWithPosts
                        mutableUiStateFlow.update {
                            it.copy(
                                sensitiveImagesBlurred = sensitiveImagesBlurred,
                                timelineItems = createTimelineItems(),
                                userPosts = if (myProfile == null) it.userPosts else createUserPosts(myProfile),
                            )
                        }
                    }

                    override fun onSearchQueryChanged(query: String) {
                        mutableUiStateFlow.update { it.copy(search = it.search.copy(query = query)) }
                    }

                    override fun onSearch() {
                        search(query = mutableUiStateFlow.value.search.query)
                    }

                    override fun onClickLoadMoreSearchResult() {
                        loadMoreSearchResult()
                    }

                    override fun onClickEditProfile() {
                        viewModelScope.launch {
                            events.send { it.navigateToProfileEdit() }
                        }
                    }

                    override fun onClickRetryProfile() {
                        loadProfile()
                    }

                    override fun onClickRetryUserPosts() {
                        retryMyPosts()
                    }

                    override fun onClickFollowing() {
                        viewModelScope.launch {
                            events.send { it.navigateToFollowing() }
                        }
                    }

                    override fun onClickFollowers() {
                        viewModelScope.launch {
                            events.send { it.navigateToFollowers() }
                        }
                    }

                    override fun onClickConfirmDeleteNote() {
                        deleteNote()
                    }

                    override fun onDismissDeleteNoteDialog() {
                        // 消している途中で閉じると、結果が出る前に別の投稿を選べてしまう
                        if (noteDeletionInProgress) return
                        deletingNoteId = null
                        updateDeleteNoteDialog()
                    }

                    override fun onScreenShown() {
                        loadProfile()
                        // 読み込みより先に購読を始め、読み込み結果との間で投稿を取りこぼしにくくする
                        if (!timelineStreamStoppedByUser) collectTimelineArrivals()
                        loadTimeline(TimelineLoadTrigger.ScreenShown)
                    }
                },
            ),
        )
    val uiStateFlow: StateFlow<HomeScreenUiState> = mutableUiStateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            userApi.followingChanges.collect { change ->
                searchPaging?.updateCache { it.withFollowing(change) }
                searchStateFlow.update { state ->
                    if (state is SearchState.Loaded) {
                        state.copy(
                            users = state.users.map { user ->
                                if (user.actorUri == change.actorUri) user.copy(following = change.following) else user
                            },
                        )
                    } else {
                        state
                    }
                }
            }
        }
        viewModelScope.launch {
            timelinePaging.watch().collect { applyTimeline(it.toUserHomeTimelineResult()) }
        }
        viewModelScope.launch {
            searchStateFlow.collect { searchState ->
                mutableUiStateFlow.update { uiState ->
                    uiState.copy(search = uiState.search.copy(result = createSearchResult(searchState)))
                }
            }
        }
    }

    private fun loadProfile() {
        profileJob?.cancel()
        // 出している内容は読み直しの間も残す。戻るたびに読み込み中へ切り替わると画面がちらつく
        if (mutableUiStateFlow.value.profile !is HomeScreenUiState.Profile.Loaded) {
            mutableUiStateFlow.update { it.copy(profile = HomeScreenUiState.Profile.Loading) }
        }
        profileJob = viewModelScope.launch {
            val result = userApi.myProfile()
            val profile =
                when (result) {
                    is UserMyProfileResult.Found -> HomeScreenUiState.Profile.Loaded(
                        iconUrl = result.iconUrl,
                        displayName = result.displayName ?: result.username,
                        acct = "@${result.username}",
                        followingCount = result.followingCount.toString(),
                        followersCount = result.followersCount.toString(),
                    )

                    UserMyProfileResult.NotLoggedIn -> HomeScreenUiState.Profile.Error(NOT_LOGGED_IN_MESSAGE)

                    is UserMyProfileResult.Failure -> HomeScreenUiState.Profile.Error(result.message)
                }
            mutableUiStateFlow.update {
                it.copy(
                    profile = profile,
                    userTabIconUrl = (profile as? HomeScreenUiState.Profile.Loaded)?.iconUrl,
                )
            }
            if (result is UserMyProfileResult.Found) {
                myUsername = result.username
                loadMyPosts(result.username)
            }
        }
    }

    private fun retryMyPosts() {
        val username = myUsername ?: return
        if (profileJob?.isActive == true || myPostsRetryJob?.isActive == true) return
        myPostsRetryJob = viewModelScope.launch { loadMyPosts(username) }
    }

    private suspend fun loadMyPosts(username: String) {
        if (mutableUiStateFlow.value.userPosts is HomeScreenUiState.UserPosts.LoadFailed) {
            mutableUiStateFlow.update { it.copy(userPosts = HomeScreenUiState.UserPosts.Loading) }
        }
        when (val result = userApi.userProfile(username = username)) {
            is UserProfileResult.Found -> {
                myProfileWithPosts = result
                mutableUiStateFlow.update { it.copy(userPosts = createUserPosts(result)) }
            }

            UserProfileResult.NotFound -> myPostsFailed(MY_POSTS_NOT_FOUND_MESSAGE)

            is UserProfileResult.Failure -> myPostsFailed(result.message)
        }
    }

    /**
     * 出している投稿は読み直しに失敗しても残す
     */
    private suspend fun myPostsFailed(message: String) {
        if (mutableUiStateFlow.value.userPosts !is HomeScreenUiState.UserPosts.Loaded) {
            mutableUiStateFlow.update { it.copy(userPosts = HomeScreenUiState.UserPosts.LoadFailed) }
        }
        events.send { it.showMessage(message) }
    }

    private fun createUserPosts(profile: UserProfileResult.Found): HomeScreenUiState.UserPosts.Loaded {
        return HomeScreenUiState.UserPosts.Loaded(
            items = profile.posts.map { post ->
                val linkPreviewId = post.linkPreviewId
                createPostItemUiState(
                    iconUrl = profile.iconUrl,
                    displayName = profile.displayName,
                    acct = "@${profile.username}",
                    body = post.body,
                    linkPreviews = linkPreviewId?.let { linkPreviews[it] }.orEmpty(),
                    sensitiveImagesBlurred = sensitiveImagesBlurred,
                    blurToggledImageUrls = blurToggledImageUrls,
                    // 自分のタブに居るので、押しても移る先が無い
                    authorListener = null,
                    menuItems = listOf(),
                    onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                    onOpenImage = { url -> viewModelScope.launch { events.send { it.navigateToImage(url) } } },
                    onToggleImageBlur = { url -> toggleImageBlur(url) },
                    onVisible = {
                        if (linkPreviewId != null && post.body.linkUrls.isNotEmpty()) {
                            loadLinkPreviews(linkPreviewId)
                        }
                    },
                )
            },
            emptyMessage = if (profile.posts.isEmpty()) "表示できる投稿はありません" else null,
        )
    }

    private fun search(query: String) {
        val trimmedQuery = query.trim()
        searchJob?.cancel()
        searchWatchJob?.cancel()
        searchPaging = null
        if (trimmedQuery.isEmpty()) {
            searchStateFlow.value = SearchState.NotSearched
            return
        }
        searchStateFlow.value = SearchState.Loading
        if (isResolveQuery(trimmedQuery)) {
            searchJob = viewModelScope.launch { resolveUser(trimmedQuery) }
            return
        }
        val paging = userApi.searchUsersPaging(trimmedQuery)
        searchPaging = paging
        searchWatchJob = viewModelScope.launch {
            paging.watch().collect { applySearchResult(trimmedQuery, it.toUserSearchResult()) }
        }
        searchJob = viewModelScope.launch {
            when (val result = paging.refresh()) {
                is UpdateOperationResponseResult.Success -> {
                    result.result.data?.let { applySearchResult(trimmedQuery, it.toUserSearchResult()) }
                }

                is UpdateOperationResponseResult.Error -> {
                    searchWatchJob?.cancel()
                    searchStateFlow.value = SearchState.NotSearched
                    events.send { it.showMessage(result.e?.message ?: NETWORK_ERROR_MESSAGE) }
                }

                is UpdateOperationResponseResult.NoHasMore -> Unit
            }
        }
    }

    private fun collectTimelineArrivals() {
        if (timelineArrivalsJob?.isActive == true) return
        timelineArrivalsJob = viewModelScope.launch {
            userApi.homeTimelineStream().collect { event ->
                when (event) {
                    HomeTimelineStreamEvent.Subscribing -> updateTimelineStreamStatus(HomeScreenUiState.TimelineStreamStatus.Receiving)

                    is HomeTimelineStreamEvent.Arrived -> {
                        postsArrivedSinceRefresh = listOf(event.post) + postsArrivedSinceRefresh
                        timelinePaging.updateCache { it.withArrivedPosts(listOf(event.post)) }
                    }

                    HomeTimelineStreamEvent.WaitingToReconnect -> {
                        updateTimelineStreamStatus(HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect)
                    }

                    HomeTimelineStreamEvent.GaveUp -> {
                        updateTimelineStreamStatus(HomeScreenUiState.TimelineStreamStatus.Disconnected)
                        // 受け手が居ないと送り終わらず購読の Job が残り、再接続で始め直せなくなる
                        viewModelScope.launch { events.send { it.showMessage(TIMELINE_STREAM_DISCONNECTED_MESSAGE) } }
                    }
                }
            }
        }
    }

    private fun stopTimelineArrivals() {
        timelineStreamStoppedByUser = true
        timelineArrivalsJob?.cancel()
        updateTimelineStreamStatus(HomeScreenUiState.TimelineStreamStatus.Disconnected)
    }

    private fun updateTimelineStreamStatus(status: HomeScreenUiState.TimelineStreamStatus) {
        timelineStreamConnectionStatus = status
        publishTimelineStreamStatus()
    }

    private fun updateTimelineCatchingUpAfterReconnect(catchingUp: Boolean) {
        timelineCatchingUpAfterReconnect = catchingUp
        publishTimelineStreamStatus()
    }

    private fun publishTimelineStreamStatus() {
        val status = if (
            timelineCatchingUpAfterReconnect &&
            timelineStreamConnectionStatus == HomeScreenUiState.TimelineStreamStatus.Receiving
        ) {
            HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect
        } else {
            timelineStreamConnectionStatus
        }
        mutableUiStateFlow.update { it.copy(timelineStreamStatus = status) }
    }

    /**
     * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
     */
    private fun applySearchResult(
        query: String,
        result: UserSearchResult.Success,
    ) {
        searchStateFlow.value = SearchState.Loaded(query = query, users = result.users, nextCursor = result.nextCursor)
    }

    /**
     * `@user@host` と URL は名前の一部ではなく、1 人を指す書き方として引く
     */
    private fun isResolveQuery(query: String): Boolean =
        query.startsWith("https://") || query.startsWith("http://") || query.removePrefix("@").contains('@')

    private suspend fun resolveUser(query: String) {
        when (val result = userApi.resolveUser(query)) {
            is UserResolveResult.Found -> {
                searchStateFlow.value = SearchState.Loaded(query = query, users = listOf(result.user), nextCursor = null)
            }

            UserResolveResult.NotFound -> {
                searchStateFlow.value = SearchState.Loaded(query = query, users = listOf(), nextCursor = null)
            }

            is UserResolveResult.Failure -> {
                searchStateFlow.value = SearchState.NotSearched
                events.send { it.showMessage(result.message) }
            }
        }
    }

    private fun loadMoreSearchResult() {
        if (searchJob?.isActive == true) return
        val paging = searchPaging ?: return
        val loaded = searchStateFlow.value as? SearchState.Loaded ?: return
        if (loaded.nextCursor == null) return
        searchJob = viewModelScope.launch {
            when (val result = paging.fetchNext()) {
                is UpdateOperationResponseResult.Success -> {
                    result.result.data?.let { applySearchResult(loaded.query, it.toUserSearchResult()) }
                }

                is UpdateOperationResponseResult.Error -> {
                    events.send { it.showMessage(result.e?.message ?: NETWORK_ERROR_MESSAGE) }
                }

                is UpdateOperationResponseResult.NoHasMore -> Unit
            }
        }
    }

    private fun createSearchResult(searchState: SearchState): HomeScreenUiState.SearchResult {
        return when (searchState) {
            SearchState.NotSearched -> HomeScreenUiState.SearchResult.NotSearched

            SearchState.Loading -> HomeScreenUiState.SearchResult.Loading

            is SearchState.Loaded -> HomeScreenUiState.SearchResult.Loaded(
                accounts = searchState.users.map { user -> createSearchAccount(user) },
                loadMoreButtonVisible = searchState.nextCursor != null,
            )
        }
    }

    private fun loadTimeline(trigger: TimelineLoadTrigger) {
        val append = trigger == TimelineLoadTrigger.Append
        if (append && timelineJob?.isActive == true) return
        if (append && timelineNextCursor == null) return
        timelineJob?.cancel()
        // 読み直しを別の読み直しで打ち切ったときに、再接続中の表示が残らないようにする
        if (!append) updateTimelineCatchingUpAfterReconnect(trigger == TimelineLoadTrigger.Reconnect)
        mutableUiStateFlow.update {
            it.copy(
                timelineRefreshing = trigger == TimelineLoadTrigger.PullToRefresh,
                timelineFooter = if (append) HomeScreenUiState.TimelineFooter.Loading else HomeScreenUiState.TimelineFooter.None,
            )
        }
        if (!append) postsArrivedSinceRefresh = listOf()
        timelineJob = viewModelScope.launch {
            try {
                val result = if (append) timelinePaging.fetchNext() else timelinePaging.refresh()
                when (result) {
                    is UpdateOperationResponseResult.Success -> {
                        val timeline = result.result.data?.toUserHomeTimelineResult()
                        if (timeline != null) applyTimeline(timeline)
                        if (!append && postsArrivedSinceRefresh.isNotEmpty()) {
                            timelinePaging.updateCache { it.withArrivedPosts(postsArrivedSinceRefresh) }
                        }
                        if (timeline == UserHomeTimelineResult.NotLoggedIn) {
                            timelineFailed(append, NOT_LOGGED_IN_MESSAGE)
                        } else {
                            mutableUiStateFlow.update { it.copy(timelineFooter = HomeScreenUiState.TimelineFooter.None) }
                        }
                    }

                    is UpdateOperationResponseResult.Error -> timelineFailed(append, result.e?.message ?: NETWORK_ERROR_MESSAGE)

                    is UpdateOperationResponseResult.NoHasMore -> {
                        mutableUiStateFlow.update { it.copy(timelineFooter = HomeScreenUiState.TimelineFooter.None) }
                    }
                }
            } finally {
                if (currentCoroutineContext().isActive) {
                    mutableUiStateFlow.update { it.copy(timelineRefreshing = false) }
                    if (trigger == TimelineLoadTrigger.Reconnect) updateTimelineCatchingUpAfterReconnect(false)
                }
            }
        }
    }

    /**
     * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
     */
    private fun applyTimeline(result: UserHomeTimelineResult) {
        val posts =
            when (result) {
                is UserHomeTimelineResult.Success -> result.posts
                UserHomeTimelineResult.NotLoggedIn -> listOf()
            }
        timelineNextCursor = (result as? UserHomeTimelineResult.Success)?.nextCursor
        // ページの境目で同じ投稿が両方のページに入ることがある
        timelinePosts = posts.distinctBy { it.id }
        mutableUiStateFlow.update { it.copy(timelineItems = createTimelineItems()) }
    }

    private fun createTimelineItems(): List<PostItemUiState> {
        return timelinePosts.map { post ->
            val previews = linkPreviews[post.id].orEmpty()
            createPostItemUiState(
                iconUrl = post.iconUrl,
                displayName = post.displayName,
                acct = if (post.host == null) "@${post.username}" else "@${post.username}@${post.host}",
                body = post.body,
                linkPreviews = previews,
                sensitiveImagesBlurred = sensitiveImagesBlurred,
                blurToggledImageUrls = blurToggledImageUrls,
                authorListener = object : PostItemUiState.AuthorListener {
                    override fun onClick() {
                        val host = post.host
                        viewModelScope.launch {
                            events.send { it.navigateToUserProfile(if (host == null) post.username else "${post.username}@$host") }
                        }
                    }
                },
                menuItems = createTimelineMenuItems(post),
                onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                onOpenImage = { url -> viewModelScope.launch { events.send { it.navigateToImage(url) } } },
                onToggleImageBlur = { url -> toggleImageBlur(url) },
                onVisible = {
                    if (post.body.linkUrls.isNotEmpty()) {
                        loadLinkPreviews(post.id)
                    }
                },
            )
        }
    }

    private fun createTimelineMenuItems(post: UserHomeTimelineResult.Post): List<PostItemUiState.MenuItem> {
        val deletableNoteId = post.deletableNoteId ?: return listOf()
        return listOf(
            PostItemUiState.MenuItem(
                label = "削除",
                listener = object : PostItemUiState.MenuItemListener {
                    override fun onClick() {
                        if (noteDeletionInProgress) return
                        deletingNoteId = deletableNoteId
                        updateDeleteNoteDialog()
                    }
                },
            ),
        )
    }

    private fun deleteNote() {
        val noteId = deletingNoteId ?: return
        if (noteDeletionInProgress) return
        noteDeletionInProgress = true
        updateDeleteNoteDialog()
        viewModelScope.launch {
            try {
                val error = userApi.deleteNote(noteId)
                if (error == null) {
                    timelinePaging.updateCache { it.withoutNote(noteId) }
                    postsArrivedSinceRefresh = postsArrivedSinceRefresh.filterNot { it.deletableNoteId == noteId }
                    events.send { it.showMessage("投稿を削除しました") }
                } else {
                    events.send { it.showMessage(error) }
                }
            } finally {
                noteDeletionInProgress = false
                deletingNoteId = null
                updateDeleteNoteDialog()
            }
        }
    }

    private fun updateDeleteNoteDialog() {
        mutableUiStateFlow.update {
            it.copy(
                deleteNoteDialog = if (deletingNoteId == null) {
                    null
                } else {
                    HomeScreenUiState.DeleteNoteDialog(deleteButtonEnabled = !noteDeletionInProgress)
                },
            )
        }
    }

    private fun toggleImageBlur(url: String) {
        blurToggledImageUrls = if (url in blurToggledImageUrls) blurToggledImageUrls - url else blurToggledImageUrls + url
        val myProfile = myProfileWithPosts
        mutableUiStateFlow.update {
            it.copy(
                timelineItems = createTimelineItems(),
                userPosts = if (myProfile == null) it.userPosts else createUserPosts(myProfile),
            )
        }
    }

    private fun loadLinkPreviews(postId: String) {
        if (postId in linkPreviewRequestedPostIds) return
        linkPreviewRequestedPostIds = linkPreviewRequestedPostIds + postId
        viewModelScope.launch {
            when (val result = userApi.linkPreviews(postId)) {
                is UserLinkPreviewsResult.Success -> {
                    linkPreviews = linkPreviews + (postId to result.previews)
                    val myProfile = myProfileWithPosts
                    mutableUiStateFlow.update {
                        it.copy(
                            timelineItems = createTimelineItems(),
                            userPosts = if (myProfile == null) it.userPosts else createUserPosts(myProfile),
                        )
                    }
                }

                // カードは URL だけでも出ているので、取れなくても知らせない。次に画面に入ったときに取り直す
                is UserLinkPreviewsResult.Failure,
                UserLinkPreviewsResult.NotLoggedIn,
                -> linkPreviewRequestedPostIds = linkPreviewRequestedPostIds - postId
            }
        }
    }

    private suspend fun timelineFailed(append: Boolean, message: String) {
        if (append) {
            mutableUiStateFlow.update { it.copy(timelineFooter = HomeScreenUiState.TimelineFooter.Error(message)) }
        } else {
            events.send { it.showMessage(message) }
        }
    }

    private fun createSearchAccount(user: UserSearchResult.User): HomeScreenUiState.SearchAccount {
        return HomeScreenUiState.SearchAccount(
            followButtonText = if (user.actorUri == null) {
                null
            } else if (user.following) {
                "フォロー中・解除"
            } else {
                "フォロー"
            },
            followButtonEnabled = !followingChangeInProgress,
            onClickFollow = { changeFollowing(user) },
            iconUrl = user.iconUrl,
            displayName = user.displayName,
            acct = if (user.isLocal) "@${user.username}" else "@${user.username}@${user.host}",
            listener =
            object : HomeScreenUiState.SearchAccountListener {
                override fun onClick() {
                    viewModelScope.launch {
                        events.send { it.navigateToUserProfile(if (user.isLocal) user.username else "${user.username}@${user.host}") }
                    }
                }
            },
        )
    }

    private fun changeFollowing(user: UserSearchResult.User) {
        val actorUri = user.actorUri ?: return
        if (followingChangeInProgress) return
        followingChangeInProgress = true
        refreshSearchButtons()
        viewModelScope.launch {
            try {
                val error = userApi.setFollowing(actorUri, followed = !user.following)
                if (error == null) {
                    loadProfile()
                } else {
                    events.send { it.showMessage(error) }
                }
            } finally {
                followingChangeInProgress = false
                refreshSearchButtons()
            }
        }
    }

    private fun refreshSearchButtons() {
        mutableUiStateFlow.update { state ->
            state.copy(search = state.search.copy(result = createSearchResult(searchStateFlow.value)))
        }
    }

    interface Event {
        fun navigateToTab(tab: HomeScreenUiState.Tab)

        fun scrollTabToTop(tab: HomeScreenUiState.Tab)

        fun navigateToPost()

        fun navigateToSettings()

        fun navigateToUserProfile(username: String)

        fun navigateToProfileEdit()

        fun navigateToFollowing()

        fun navigateToFollowers()

        fun showMessage(message: String)

        fun openUrl(url: String)

        fun navigateToImage(url: String)
    }

    private companion object {
        const val NOT_LOGGED_IN_MESSAGE = "ログインしていません"
        const val NETWORK_ERROR_MESSAGE = "ネットワークエラー"
        const val MY_POSTS_NOT_FOUND_MESSAGE = "投稿を読み込めませんでした"
        const val TIMELINE_STREAM_DISCONNECTED_MESSAGE = "新着投稿の受信が切れました"
    }

    private enum class TimelineLoadTrigger {
        PullToRefresh,
        ScreenShown,
        Reconnect,
        Append,
    }

    private sealed interface SearchState {
        data object NotSearched : SearchState

        data object Loading : SearchState

        data class Loaded(
            val query: String,
            val users: List<UserSearchResult.User>,
            val nextCursor: String?,
        ) : SearchState
    }
}
