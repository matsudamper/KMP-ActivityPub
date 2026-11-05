package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
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
import net.matsudamper.kmp.activitypub.frontend.screen.PostedAtRefreshTicker
import net.matsudamper.kmp.activitypub.frontend.screen.createPostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class HomeScreenViewModel(
    @InjectedParam private val selectedTab: HomeScreenUiState.Tab,
    private val userApi: UserApi,
    private val postedAtRefreshTicker: PostedAtRefreshTicker,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                selectedTab = selectedTab,
                timelinePosts = listOf(),
                timelineNextCursor = null,
                timelineRefreshing = false,
                timelineAppending = false,
                timelineAppendErrorMessage = null,
                postsArrivedSinceRefresh = listOf(),
                timelineStreamConnectionStatus = HomeScreenUiState.TimelineStreamStatus.Receiving,
                timelineCatchingUpAfterReconnect = false,
                timelineStreamStoppedByUser = false,
                linkPreviewRequestedPostIds = setOf(),
                linkPreviews = mapOf(),
                sensitiveImagesBlurred = true,
                blurToggledImageUrls = setOf(),
                searchQuery = "",
                search = SearchState.NotSearched,
                followingChangeInProgress = false,
                profile = ProfileState.Loading,
                myUsername = null,
                myPosts = MyPostsState.Loading,
            ),
        )
    private val timelinePaging = userApi.homeTimelinePaging()
    private var searchPaging: CursorPagingModel<HomeScreenSearchUsersQuery.Data>? = null
    private var searchJob: Job? = null
    private var searchWatchJob: Job? = null
    private var profileJob: Job? = null
    private var myPostsRetryJob: Job? = null
    private var timelineJob: Job? = null
    private var timelineArrivalsJob: Job? = null

    private val listener = object : HomeScreenUiState.Listener {
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
            val reselected = viewModelStateFlow.value.selectedTab == tab
            viewModelScope.launch {
                if (reselected) {
                    events.send { it.scrollTabToTop(tab) }
                } else {
                    events.send { it.navigateToTab(tab) }
                }
            }
        }

        override fun onTabChanged(tab: HomeScreenUiState.Tab) {
            viewModelStateFlow.update { it.copy(selectedTab = tab) }
        }

        override fun onRefreshTimeline() {
            loadTimeline(TimelineLoadTrigger.PullToRefresh)
        }

        override fun onScrolledToTimelineEnd() {
            if (viewModelStateFlow.value.timelineAppendErrorMessage != null) return
            loadTimeline(TimelineLoadTrigger.Append)
        }

        override fun onClickRetryTimelineLoadMore() {
            loadTimeline(TimelineLoadTrigger.Append)
        }

        override fun onClickTimelineStreamStatus() {
            when (viewModelStateFlow.value.timelineStreamStatus()) {
                HomeScreenUiState.TimelineStreamStatus.Receiving,
                HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect,
                -> stopTimelineArrivals()

                HomeScreenUiState.TimelineStreamStatus.Disconnected -> {
                    viewModelStateFlow.update { it.copy(timelineStreamStoppedByUser = false) }
                    collectTimelineArrivals()
                    // 切れていた間の投稿は流れてこないので、読み直して埋める
                    loadTimeline(TimelineLoadTrigger.Reconnect)
                }
            }
        }

        override fun onClickSensitiveImagesVisibility() {
            viewModelStateFlow.update {
                it.copy(
                    sensitiveImagesBlurred = !it.sensitiveImagesBlurred,
                    blurToggledImageUrls = setOf(),
                )
            }
        }

        override fun onSearchQueryChanged(query: String) {
            viewModelStateFlow.update { it.copy(searchQuery = query) }
        }

        override fun onSearch() {
            search(query = viewModelStateFlow.value.searchQuery)
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

        override fun onScreenShown() {
            loadProfile()
            // 読み込みより先に購読を始め、読み込み結果との間で投稿を取りこぼしにくくする
            if (!viewModelStateFlow.value.timelineStreamStoppedByUser) collectTimelineArrivals()
            loadTimeline(TimelineLoadTrigger.ScreenShown)
        }
    }

    val uiStateFlow: StateFlow<HomeScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                combine(
                    viewModelStateFlow,
                    postedAtRefreshTicker.ticks.onStart { emit(Unit) },
                ) { viewModelState, _ ->
                    createUiState(viewModelState)
                }.collect { uiState ->
                    uiStateFlow.value = uiState
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            userApi.followingChanges.collect { change ->
                searchPaging?.updateCache { it.withFollowing(change) }
                viewModelStateFlow.update { state ->
                    val search = state.search
                    if (search is SearchState.Loaded) {
                        state.copy(
                            search = search.copy(
                                users = search.users.map { user ->
                                    if (user.actorUri == change.actorUri) user.copy(following = change.following) else user
                                },
                            ),
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
    }

    private fun loadProfile() {
        profileJob?.cancel()
        // 出している内容は読み直しの間も残す。戻るたびに読み込み中へ切り替わると画面がちらつく
        viewModelStateFlow.update {
            if (it.profile is ProfileState.Loaded) it else it.copy(profile = ProfileState.Loading)
        }
        profileJob = viewModelScope.launch {
            when (val result = userApi.myProfile()) {
                is UserMyProfileResult.Found -> {
                    viewModelStateFlow.update {
                        it.copy(profile = ProfileState.Loaded(result), myUsername = result.username)
                    }
                    loadMyPosts(result.username)
                }

                UserMyProfileResult.NotLoggedIn -> {
                    viewModelStateFlow.update { it.copy(profile = ProfileState.Failed(NOT_LOGGED_IN_MESSAGE)) }
                }

                is UserMyProfileResult.Failure -> {
                    viewModelStateFlow.update { it.copy(profile = ProfileState.Failed(result.message)) }
                }
            }
        }
    }

    private fun retryMyPosts() {
        val username = viewModelStateFlow.value.myUsername ?: return
        if (profileJob?.isActive == true || myPostsRetryJob?.isActive == true) return
        myPostsRetryJob = viewModelScope.launch { loadMyPosts(username) }
    }

    private suspend fun loadMyPosts(username: String) {
        viewModelStateFlow.update {
            if (it.myPosts is MyPostsState.LoadFailed) it.copy(myPosts = MyPostsState.Loading) else it
        }
        when (val result = userApi.userProfile(username = username)) {
            is UserProfileResult.Found -> {
                viewModelStateFlow.update { it.copy(myPosts = MyPostsState.Loaded(result)) }
            }

            UserProfileResult.NotFound -> myPostsFailed(MY_POSTS_NOT_FOUND_MESSAGE)

            is UserProfileResult.Failure -> myPostsFailed(result.message)
        }
    }

    /**
     * 出している投稿は読み直しに失敗しても残す
     */
    private suspend fun myPostsFailed(message: String) {
        viewModelStateFlow.update {
            if (it.myPosts is MyPostsState.Loaded) it else it.copy(myPosts = MyPostsState.LoadFailed)
        }
        events.send { it.showMessage(message) }
    }

    private fun search(query: String) {
        val trimmedQuery = query.trim()
        searchJob?.cancel()
        searchWatchJob?.cancel()
        searchPaging = null
        if (trimmedQuery.isEmpty()) {
            updateSearchState(SearchState.NotSearched)
            return
        }
        updateSearchState(SearchState.Loading)
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
                    updateSearchState(SearchState.NotSearched)
                    events.send { it.showMessage(result.e?.message ?: NETWORK_ERROR_MESSAGE) }
                }

                is UpdateOperationResponseResult.NoHasMore -> Unit
            }
        }
    }

    private fun updateSearchState(search: SearchState) {
        viewModelStateFlow.update { it.copy(search = search) }
    }

    private fun collectTimelineArrivals() {
        if (timelineArrivalsJob?.isActive == true) return
        timelineArrivalsJob = viewModelScope.launch {
            userApi.homeTimelineStream().collect { event ->
                when (event) {
                    HomeTimelineStreamEvent.Subscribing -> updateTimelineStreamStatus(HomeScreenUiState.TimelineStreamStatus.Receiving)

                    is HomeTimelineStreamEvent.Arrived -> {
                        viewModelStateFlow.update {
                            it.copy(postsArrivedSinceRefresh = listOf(event.post) + it.postsArrivedSinceRefresh)
                        }
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
        timelineArrivalsJob?.cancel()
        viewModelStateFlow.update {
            it.copy(
                timelineStreamStoppedByUser = true,
                timelineStreamConnectionStatus = HomeScreenUiState.TimelineStreamStatus.Disconnected,
            )
        }
    }

    private fun updateTimelineStreamStatus(status: HomeScreenUiState.TimelineStreamStatus) {
        viewModelStateFlow.update { it.copy(timelineStreamConnectionStatus = status) }
    }

    /**
     * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
     */
    private fun applySearchResult(
        query: String,
        result: UserSearchResult.Success,
    ) {
        updateSearchState(SearchState.Loaded(query = query, users = result.users, nextCursor = result.nextCursor))
    }

    /**
     * `@user@host` と URL は名前の一部ではなく、1 人を指す書き方として引く
     */
    private fun isResolveQuery(query: String): Boolean =
        query.startsWith("https://") || query.startsWith("http://") || query.removePrefix("@").contains('@')

    private suspend fun resolveUser(query: String) {
        when (val result = userApi.resolveUser(query)) {
            is UserResolveResult.Found -> {
                updateSearchState(SearchState.Loaded(query = query, users = listOf(result.user), nextCursor = null))
            }

            UserResolveResult.NotFound -> {
                updateSearchState(SearchState.Loaded(query = query, users = listOf(), nextCursor = null))
            }

            is UserResolveResult.Failure -> {
                updateSearchState(SearchState.NotSearched)
                events.send { it.showMessage(result.message) }
            }
        }
    }

    private fun loadMoreSearchResult() {
        if (searchJob?.isActive == true) return
        val paging = searchPaging ?: return
        val loaded = viewModelStateFlow.value.search as? SearchState.Loaded ?: return
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

    private fun loadTimeline(trigger: TimelineLoadTrigger) {
        val append = trigger == TimelineLoadTrigger.Append
        if (append && timelineJob?.isActive == true) return
        if (append && viewModelStateFlow.value.timelineNextCursor == null) return
        timelineJob?.cancel()
        viewModelStateFlow.update {
            it.copy(
                timelineRefreshing = trigger == TimelineLoadTrigger.PullToRefresh,
                timelineAppending = append,
                timelineAppendErrorMessage = null,
                // 読み直しを別の読み直しで打ち切ったときに、再接続中の表示が残らないようにする
                timelineCatchingUpAfterReconnect = if (append) {
                    it.timelineCatchingUpAfterReconnect
                } else {
                    trigger == TimelineLoadTrigger.Reconnect
                },
                postsArrivedSinceRefresh = if (append) it.postsArrivedSinceRefresh else listOf(),
            )
        }
        timelineJob = viewModelScope.launch {
            try {
                val result = if (append) timelinePaging.fetchNext() else timelinePaging.refresh()
                when (result) {
                    is UpdateOperationResponseResult.Success -> {
                        val timeline = result.result.data?.toUserHomeTimelineResult()
                        if (timeline != null) applyTimeline(timeline)
                        val postsArrivedSinceRefresh = viewModelStateFlow.value.postsArrivedSinceRefresh
                        if (!append && postsArrivedSinceRefresh.isNotEmpty()) {
                            timelinePaging.updateCache { it.withArrivedPosts(postsArrivedSinceRefresh) }
                        }
                        if (timeline == UserHomeTimelineResult.NotLoggedIn) {
                            timelineFailed(append, NOT_LOGGED_IN_MESSAGE)
                        }
                    }

                    is UpdateOperationResponseResult.Error -> timelineFailed(append, result.e?.message ?: NETWORK_ERROR_MESSAGE)

                    is UpdateOperationResponseResult.NoHasMore -> Unit
                }
            } finally {
                if (currentCoroutineContext().isActive) {
                    viewModelStateFlow.update {
                        it.copy(
                            timelineRefreshing = false,
                            timelineAppending = false,
                            timelineCatchingUpAfterReconnect = if (trigger == TimelineLoadTrigger.Reconnect) {
                                false
                            } else {
                                it.timelineCatchingUpAfterReconnect
                            },
                        )
                    }
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
        viewModelStateFlow.update {
            it.copy(
                timelineNextCursor = (result as? UserHomeTimelineResult.Success)?.nextCursor,
                // ページの境目で同じ投稿が両方のページに入ることがある
                timelinePosts = posts.distinctBy { post -> post.id },
            )
        }
    }

    private fun toggleImageBlur(url: String) {
        viewModelStateFlow.update {
            val urls = it.blurToggledImageUrls
            it.copy(blurToggledImageUrls = if (url in urls) urls - url else urls + url)
        }
    }

    private fun loadLinkPreviews(postId: String) {
        if (postId in viewModelStateFlow.value.linkPreviewRequestedPostIds) return
        viewModelStateFlow.update { it.copy(linkPreviewRequestedPostIds = it.linkPreviewRequestedPostIds + postId) }
        viewModelScope.launch {
            when (val result = userApi.linkPreviews(postId)) {
                is UserLinkPreviewsResult.Success -> {
                    viewModelStateFlow.update { it.copy(linkPreviews = it.linkPreviews + (postId to result.previews)) }
                }

                // カードは URL だけでも出ているので、取れなくても知らせない。次に画面に入ったときに取り直す
                is UserLinkPreviewsResult.Failure,
                UserLinkPreviewsResult.NotLoggedIn,
                -> viewModelStateFlow.update { it.copy(linkPreviewRequestedPostIds = it.linkPreviewRequestedPostIds - postId) }
            }
        }
    }

    private suspend fun timelineFailed(append: Boolean, message: String) {
        if (append) {
            viewModelStateFlow.update { it.copy(timelineAppendErrorMessage = message) }
        } else {
            events.send { it.showMessage(message) }
        }
    }

    private fun changeFollowing(user: UserSearchResult.User) {
        val actorUri = user.actorUri ?: return
        if (viewModelStateFlow.value.followingChangeInProgress) return
        viewModelStateFlow.update { it.copy(followingChangeInProgress = true) }
        viewModelScope.launch {
            try {
                val error = userApi.setFollowing(actorUri, followed = !user.following)
                if (error == null) {
                    loadProfile()
                } else {
                    events.send { it.showMessage(error) }
                }
            } finally {
                viewModelStateFlow.update { it.copy(followingChangeInProgress = false) }
            }
        }
    }

    private fun createUiState(viewModelState: ViewModelState): HomeScreenUiState {
        val profile = viewModelState.profile
        return HomeScreenUiState(
            selectedTab = viewModelState.selectedTab,
            timelineItems = createTimelineItems(viewModelState),
            timelineRefreshing = viewModelState.timelineRefreshing,
            timelineFooter = createTimelineFooter(viewModelState),
            timelineStreamStatus = viewModelState.timelineStreamStatus(),
            sensitiveImagesBlurred = viewModelState.sensitiveImagesBlurred,
            search = HomeScreenUiState.Search(
                query = viewModelState.searchQuery,
                result = createSearchResult(viewModelState),
            ),
            profile = createProfile(profile),
            userPosts = createUserPosts(viewModelState),
            userTabIconUrl = (profile as? ProfileState.Loaded)?.profile?.iconUrl,
            listener = listener,
        )
    }

    private fun createTimelineFooter(viewModelState: ViewModelState): HomeScreenUiState.TimelineFooter {
        val appendErrorMessage = viewModelState.timelineAppendErrorMessage
        return when {
            appendErrorMessage != null -> HomeScreenUiState.TimelineFooter.Error(appendErrorMessage)
            viewModelState.timelineAppending -> HomeScreenUiState.TimelineFooter.Loading
            else -> HomeScreenUiState.TimelineFooter.None
        }
    }

    private fun createProfile(profile: ProfileState): HomeScreenUiState.Profile {
        return when (profile) {
            ProfileState.Loading -> HomeScreenUiState.Profile.Loading

            is ProfileState.Loaded -> HomeScreenUiState.Profile.Loaded(
                iconUrl = profile.profile.iconUrl,
                displayName = profile.profile.displayName ?: profile.profile.username,
                acct = "@${profile.profile.username}",
                followingCount = profile.profile.followingCount.toString(),
                followersCount = profile.profile.followersCount.toString(),
            )

            is ProfileState.Failed -> HomeScreenUiState.Profile.Error(profile.message)
        }
    }

    private fun createUserPosts(viewModelState: ViewModelState): HomeScreenUiState.UserPosts {
        return when (val myPosts = viewModelState.myPosts) {
            MyPostsState.Loading -> HomeScreenUiState.UserPosts.Loading

            MyPostsState.LoadFailed -> HomeScreenUiState.UserPosts.LoadFailed

            is MyPostsState.Loaded -> {
                val profile = myPosts.profile
                HomeScreenUiState.UserPosts.Loaded(
                    items = profile.posts.map { post ->
                        val linkPreviewId = post.linkPreviewId
                        createPostItemUiState(
                            iconUrl = profile.iconUrl,
                            displayName = profile.displayName,
                            acct = "@${profile.username}",
                            body = post.body,
                            linkPreviews = linkPreviewId?.let { viewModelState.linkPreviews[it] }.orEmpty(),
                            sensitiveImagesBlurred = viewModelState.sensitiveImagesBlurred,
                            blurToggledImageUrls = viewModelState.blurToggledImageUrls,
                            // 自分のタブに居るので、押しても移る先が無い
                            authorListener = null,
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
        }
    }

    private fun createTimelineItems(viewModelState: ViewModelState): List<PostItemUiState> {
        return viewModelState.timelinePosts.map { post ->
            createPostItemUiState(
                iconUrl = post.iconUrl,
                displayName = post.displayName,
                acct = if (post.host == null) "@${post.username}" else "@${post.username}@${post.host}",
                body = post.body,
                linkPreviews = viewModelState.linkPreviews[post.id].orEmpty(),
                sensitiveImagesBlurred = viewModelState.sensitiveImagesBlurred,
                blurToggledImageUrls = viewModelState.blurToggledImageUrls,
                authorListener = object : PostItemUiState.AuthorListener {
                    override fun onClick() {
                        val host = post.host
                        viewModelScope.launch {
                            events.send { it.navigateToUserProfile(if (host == null) post.username else "${post.username}@$host") }
                        }
                    }
                },
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

    private fun createSearchResult(viewModelState: ViewModelState): HomeScreenUiState.SearchResult {
        return when (val search = viewModelState.search) {
            SearchState.NotSearched -> HomeScreenUiState.SearchResult.NotSearched

            SearchState.Loading -> HomeScreenUiState.SearchResult.Loading

            is SearchState.Loaded -> HomeScreenUiState.SearchResult.Loaded(
                accounts = search.users.map { user ->
                    createSearchAccount(user, followButtonEnabled = !viewModelState.followingChangeInProgress)
                },
                loadMoreButtonVisible = search.nextCursor != null,
            )
        }
    }

    private fun createSearchAccount(
        user: UserSearchResult.User,
        followButtonEnabled: Boolean,
    ): HomeScreenUiState.SearchAccount {
        return HomeScreenUiState.SearchAccount(
            followButtonText = if (user.actorUri == null) {
                null
            } else if (user.following) {
                "フォロー中・解除"
            } else {
                "フォロー"
            },
            followButtonEnabled = followButtonEnabled,
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

    /**
     * 再接続直後は購読がつながっていても切れていた間の投稿を読み直し終えるまでは追いついていないので、再接続中として見せる
     */
    private fun ViewModelState.timelineStreamStatus(): HomeScreenUiState.TimelineStreamStatus {
        return if (
            timelineCatchingUpAfterReconnect &&
            timelineStreamConnectionStatus == HomeScreenUiState.TimelineStreamStatus.Receiving
        ) {
            HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect
        } else {
            timelineStreamConnectionStatus
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

    /**
     * @param timelineStreamStoppedByUser 自分で切ったときは、画面に戻ってきてもつなぎ直さない
     * @param postsArrivedSinceRefresh 読み直しの結果は要求した時点のものなので、その間に流れてきた投稿を先頭に足し直す
     * @param blurToggledImageUrls 一括で切り替えたら、画像ごとに開け閉めした分は捨てて揃える
     * @param myUsername 投稿の読み直しに使う。プロフィールの読み直しに失敗しても残す
     */
    private data class ViewModelState(
        val selectedTab: HomeScreenUiState.Tab,
        val timelinePosts: List<UserHomeTimelineResult.Post>,
        val timelineNextCursor: String?,
        val timelineRefreshing: Boolean,
        val timelineAppending: Boolean,
        val timelineAppendErrorMessage: String?,
        val postsArrivedSinceRefresh: List<UserHomeTimelineResult.Post>,
        val timelineStreamConnectionStatus: HomeScreenUiState.TimelineStreamStatus,
        val timelineCatchingUpAfterReconnect: Boolean,
        val timelineStreamStoppedByUser: Boolean,
        val linkPreviewRequestedPostIds: Set<String>,
        val linkPreviews: Map<String, List<UserLinkPreviewsResult.LinkPreview>>,
        val sensitiveImagesBlurred: Boolean,
        val blurToggledImageUrls: Set<String>,
        val searchQuery: String,
        val search: SearchState,
        val followingChangeInProgress: Boolean,
        val profile: ProfileState,
        val myUsername: String?,
        val myPosts: MyPostsState,
    )

    private sealed interface ProfileState {
        data object Loading : ProfileState

        data class Loaded(
            val profile: UserMyProfileResult.Found,
        ) : ProfileState

        data class Failed(
            val message: String,
        ) : ProfileState
    }

    private sealed interface MyPostsState {
        data object Loading : MyPostsState

        data class Loaded(
            val profile: UserProfileResult.Found,
        ) : MyPostsState

        data object LoadFailed : MyPostsState
    }
}
