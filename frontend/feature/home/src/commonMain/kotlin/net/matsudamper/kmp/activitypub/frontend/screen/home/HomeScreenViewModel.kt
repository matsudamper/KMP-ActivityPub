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
import net.matsudamper.kmp.activitypub.frontend.screen.createProfileMedia
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
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
                timeline = TimelineState(
                    posts = listOf(),
                    nextCursor = null,
                    postsArrivedSinceRefresh = listOf(),
                    loading = TimelineLoading.None,
                ),
                timelineStream = TimelineStreamState(
                    connection = TimelineStreamConnection.Receiving,
                    catchingUpAfterReconnect = false,
                ),
                linkPreviews = mapOf(),
                imageBlur = ImageBlurState(
                    sensitiveImagesBlurred = true,
                    toggledImageUrls = setOf(),
                ),
                search = SearchState(
                    query = "",
                    result = SearchResultState.NotSearched,
                    followingChangeInProgress = false,
                ),
                profile = ProfileState.Loading,
                myPosts = MyPostsState.Loading,
                userContentTab = ProfileContentTab.Posts,
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

    /**
     * 表示には使わないので ViewModelState に入れない。入れると失敗時の削除で投稿の listener が作り直され、即座に取り直しを繰り返す
     */
    private var linkPreviewRequestedPostIds = setOf<String>()

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
            if (viewModelStateFlow.value.timeline.loading is TimelineLoading.AppendFailed) return
            loadTimeline(TimelineLoadTrigger.Append)
        }

        override fun onClickRetryTimelineLoadMore() {
            loadTimeline(TimelineLoadTrigger.Append)
        }

        override fun onClickTimelineStreamStatus() {
            when (viewModelStateFlow.value.timelineStream.status()) {
                HomeScreenUiState.TimelineStreamStatus.Receiving,
                HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect,
                -> stopTimelineArrivals()

                HomeScreenUiState.TimelineStreamStatus.Disconnected -> {
                    collectTimelineArrivals()
                    // 切れていた間の投稿は流れてこないので、読み直して埋める
                    loadTimeline(TimelineLoadTrigger.Reconnect)
                }
            }
        }

        override fun onClickSensitiveImagesVisibility() {
            viewModelStateFlow.update {
                it.copy(
                    imageBlur = ImageBlurState(
                        sensitiveImagesBlurred = !it.imageBlur.sensitiveImagesBlurred,
                        toggledImageUrls = setOf(),
                    ),
                )
            }
        }

        override fun onSearchQueryChanged(query: String) {
            updateSearch { it.copy(query = query) }
        }

        override fun onSearch() {
            search(query = viewModelStateFlow.value.search.query)
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

        override fun onClickUserContentTab(tab: ProfileContentTab) {
            viewModelStateFlow.update { it.copy(userContentTab = tab) }
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
            if (viewModelStateFlow.value.timelineStream.connection != TimelineStreamConnection.StoppedByUser) {
                collectTimelineArrivals()
            }
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
                updateSearchResult { result ->
                    if (result is SearchResultState.Loaded) {
                        result.copy(
                            users = result.users.map { user ->
                                if (user.actorUri == change.actorUri) user.copy(following = change.following) else user
                            },
                        )
                    } else {
                        result
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
                    viewModelStateFlow.update { it.copy(profile = ProfileState.Loaded(result)) }
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
        val failed = viewModelStateFlow.value.myPosts as? MyPostsState.LoadFailed ?: return
        if (profileJob?.isActive == true || myPostsRetryJob?.isActive == true) return
        myPostsRetryJob = viewModelScope.launch { loadMyPosts(failed.username) }
    }

    private suspend fun loadMyPosts(username: String) {
        viewModelStateFlow.update {
            if (it.myPosts is MyPostsState.LoadFailed) it.copy(myPosts = MyPostsState.Loading) else it
        }
        when (val result = userApi.userProfile(username = username)) {
            is UserProfileResult.Found -> {
                viewModelStateFlow.update { it.copy(myPosts = MyPostsState.Loaded(result)) }
            }

            UserProfileResult.NotFound -> myPostsFailed(username, MY_POSTS_NOT_FOUND_MESSAGE)

            is UserProfileResult.Failure -> myPostsFailed(username, result.message)
        }
    }

    /**
     * 出している投稿は読み直しに失敗しても残す
     */
    private suspend fun myPostsFailed(username: String, message: String) {
        viewModelStateFlow.update {
            if (it.myPosts is MyPostsState.Loaded) it else it.copy(myPosts = MyPostsState.LoadFailed(username))
        }
        events.send { it.showMessage(message) }
    }

    private fun search(query: String) {
        val trimmedQuery = query.trim()
        searchJob?.cancel()
        searchWatchJob?.cancel()
        searchPaging = null
        if (trimmedQuery.isEmpty()) {
            updateSearchResult { SearchResultState.NotSearched }
            return
        }
        updateSearchResult { SearchResultState.Loading }
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
                    val data = result.result.data
                    if (data != null) applySearchResult(trimmedQuery, data.toUserSearchResult())
                }

                is UpdateOperationResponseResult.Error -> {
                    searchWatchJob?.cancel()
                    updateSearchResult { SearchResultState.NotSearched }
                    events.send { it.showMessage(result.e?.message ?: NETWORK_ERROR_MESSAGE) }
                }

                is UpdateOperationResponseResult.NoHasMore -> Unit
            }
        }
    }

    private fun updateSearch(transform: (SearchState) -> SearchState) {
        viewModelStateFlow.update { it.copy(search = transform(it.search)) }
    }

    private fun updateSearchResult(transform: (SearchResultState) -> SearchResultState) {
        updateSearch { it.copy(result = transform(it.result)) }
    }

    private fun collectTimelineArrivals() {
        if (timelineArrivalsJob?.isActive == true) return
        timelineArrivalsJob = viewModelScope.launch {
            userApi.homeTimelineStream().collect { event ->
                when (event) {
                    HomeTimelineStreamEvent.Subscribing -> updateTimelineStreamConnection(TimelineStreamConnection.Receiving)

                    is HomeTimelineStreamEvent.Arrived -> {
                        updateTimeline { it.copy(postsArrivedSinceRefresh = listOf(event.post) + it.postsArrivedSinceRefresh) }
                        timelinePaging.updateCache { it.withArrivedPosts(listOf(event.post)) }
                    }

                    HomeTimelineStreamEvent.WaitingToReconnect -> {
                        updateTimelineStreamConnection(TimelineStreamConnection.WaitingToReconnect)
                    }

                    HomeTimelineStreamEvent.GaveUp -> {
                        updateTimelineStreamConnection(TimelineStreamConnection.GaveUp)
                        // 受け手が居ないと送り終わらず購読の Job が残り、再接続で始め直せなくなる
                        viewModelScope.launch { events.send { it.showMessage(TIMELINE_STREAM_DISCONNECTED_MESSAGE) } }
                    }
                }
            }
        }
    }

    private fun stopTimelineArrivals() {
        timelineArrivalsJob?.cancel()
        updateTimelineStreamConnection(TimelineStreamConnection.StoppedByUser)
    }

    private fun updateTimelineStreamConnection(connection: TimelineStreamConnection) {
        viewModelStateFlow.update { it.copy(timelineStream = it.timelineStream.copy(connection = connection)) }
    }

    private fun updateTimelineCatchingUpAfterReconnect(catchingUp: Boolean) {
        viewModelStateFlow.update {
            it.copy(timelineStream = it.timelineStream.copy(catchingUpAfterReconnect = catchingUp))
        }
    }

    private fun updateTimeline(transform: (TimelineState) -> TimelineState) {
        viewModelStateFlow.update { it.copy(timeline = transform(it.timeline)) }
    }

    /**
     * 同じ内容を書き込んだときは watch が流れないので、取得結果はここでも反映する
     */
    private fun applySearchResult(
        query: String,
        result: UserSearchResult.Success,
    ) {
        updateSearchResult {
            SearchResultState.Loaded(query = query, users = result.users, nextCursor = result.nextCursor)
        }
    }

    /**
     * `@user@host` と URL は名前の一部ではなく、1 人を指す書き方として引く
     */
    private fun isResolveQuery(query: String): Boolean =
        query.startsWith("https://") || query.startsWith("http://") || query.removePrefix("@").contains('@')

    private suspend fun resolveUser(query: String) {
        when (val result = userApi.resolveUser(query)) {
            is UserResolveResult.Found -> {
                updateSearchResult { SearchResultState.Loaded(query = query, users = listOf(result.user), nextCursor = null) }
            }

            UserResolveResult.NotFound -> {
                updateSearchResult { SearchResultState.Loaded(query = query, users = listOf(), nextCursor = null) }
            }

            is UserResolveResult.Failure -> {
                updateSearchResult { SearchResultState.NotSearched }
                events.send { it.showMessage(result.message) }
            }
        }
    }

    private fun loadMoreSearchResult() {
        if (searchJob?.isActive == true) return
        val paging = searchPaging ?: return
        val loaded = viewModelStateFlow.value.search.result as? SearchResultState.Loaded ?: return
        if (loaded.nextCursor == null) return
        searchJob = viewModelScope.launch {
            when (val result = paging.fetchNext()) {
                is UpdateOperationResponseResult.Success -> {
                    val data = result.result.data
                    if (data != null) applySearchResult(loaded.query, data.toUserSearchResult())
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
        if (append && viewModelStateFlow.value.timeline.nextCursor == null) return
        timelineJob?.cancel()
        // 読み直しを別の読み直しで打ち切ったときに、再接続中の表示が残らないようにする
        if (!append) updateTimelineCatchingUpAfterReconnect(trigger == TimelineLoadTrigger.Reconnect)
        updateTimeline {
            it.copy(
                loading = when (trigger) {
                    TimelineLoadTrigger.PullToRefresh -> TimelineLoading.PullToRefresh

                    TimelineLoadTrigger.Append -> TimelineLoading.Append

                    TimelineLoadTrigger.ScreenShown,
                    TimelineLoadTrigger.Reconnect,
                    -> TimelineLoading.None
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
                        val postsArrivedSinceRefresh = viewModelStateFlow.value.timeline.postsArrivedSinceRefresh
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
                    updateTimeline {
                        if (it.loading is TimelineLoading.AppendFailed) it else it.copy(loading = TimelineLoading.None)
                    }
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
        updateTimeline {
            it.copy(
                nextCursor = (result as? UserHomeTimelineResult.Success)?.nextCursor,
                // ページの境目で同じ投稿が両方のページに入ることがある
                posts = posts.distinctBy { post -> post.id },
            )
        }
    }

    private fun toggleImageBlur(url: String) {
        viewModelStateFlow.update {
            val urls = it.imageBlur.toggledImageUrls
            it.copy(imageBlur = it.imageBlur.copy(toggledImageUrls = if (url in urls) urls - url else urls + url))
        }
    }

    private fun loadLinkPreviews(postId: String) {
        if (postId in linkPreviewRequestedPostIds) return
        linkPreviewRequestedPostIds = linkPreviewRequestedPostIds + postId
        viewModelScope.launch {
            when (val result = userApi.linkPreviews(postId)) {
                is UserLinkPreviewsResult.Success -> {
                    viewModelStateFlow.update { it.copy(linkPreviews = it.linkPreviews + (postId to result.previews)) }
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
            updateTimeline { it.copy(loading = TimelineLoading.AppendFailed(message)) }
        } else {
            events.send { it.showMessage(message) }
        }
    }

    private fun changeFollowing(user: UserSearchResult.User) {
        val actorUri = user.actorUri ?: return
        if (viewModelStateFlow.value.search.followingChangeInProgress) return
        updateSearch { it.copy(followingChangeInProgress = true) }
        viewModelScope.launch {
            try {
                val error = userApi.setFollowing(actorUri, followed = !user.following)
                if (error == null) {
                    loadProfile()
                } else {
                    events.send { it.showMessage(error) }
                }
            } finally {
                updateSearch { it.copy(followingChangeInProgress = false) }
            }
        }
    }

    private fun createUiState(viewModelState: ViewModelState): HomeScreenUiState {
        val profile = viewModelState.profile
        return HomeScreenUiState(
            selectedTab = viewModelState.selectedTab,
            timelineItems = createTimelineItems(viewModelState),
            timelineRefreshing = viewModelState.timeline.loading == TimelineLoading.PullToRefresh,
            timelineFooter = createTimelineFooter(viewModelState.timeline.loading),
            timelineStreamStatus = viewModelState.timelineStream.status(),
            sensitiveImagesBlurred = viewModelState.imageBlur.sensitiveImagesBlurred,
            search = HomeScreenUiState.Search(
                query = viewModelState.search.query,
                result = createSearchResult(viewModelState.search),
            ),
            profile = createProfile(profile),
            userContentTab = viewModelState.userContentTab,
            userPosts = createUserPosts(viewModelState),
            userTabIconUrl = (profile as? ProfileState.Loaded)?.profile?.iconUrl,
            listener = listener,
        )
    }

    private fun createTimelineFooter(loading: TimelineLoading): HomeScreenUiState.TimelineFooter {
        return when (loading) {
            is TimelineLoading.AppendFailed -> HomeScreenUiState.TimelineFooter.Error(loading.message)

            TimelineLoading.Append -> HomeScreenUiState.TimelineFooter.Loading

            TimelineLoading.None,
            TimelineLoading.PullToRefresh,
            -> HomeScreenUiState.TimelineFooter.None
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

            is MyPostsState.LoadFailed -> HomeScreenUiState.UserPosts.LoadFailed

            is MyPostsState.Loaded -> {
                val profile = myPosts.profile
                val media = createProfileMedia(profile.posts) { postId ->
                    viewModelScope.launch { events.send { it.navigateToPostDetail(postId) } }
                }
                HomeScreenUiState.UserPosts.Loaded(
                    items = profile.posts.map { post ->
                        val linkPreviewId = post.linkPreviewId
                        createPostItemUiState(
                            iconUrl = profile.iconUrl,
                            displayName = profile.displayName,
                            acct = "@${profile.username}",
                            body = post.body,
                            linkPreviews = linkPreviewId?.let { viewModelState.linkPreviews[it] }.orEmpty(),
                            sensitiveImagesBlurred = viewModelState.imageBlur.sensitiveImagesBlurred,
                            blurToggledImageUrls = viewModelState.imageBlur.toggledImageUrls,
                            // 自分のタブに居るので、押しても移る先が無い
                            authorListener = null,
                            postListener = null,
                            onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                            onOpenImage = { url -> viewModelScope.launch { events.send { it.navigateToImage(url) } } },
                            onToggleImageBlur = { url -> toggleImageBlur(url) },
                            onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
                            onVisible = {
                                if (linkPreviewId != null && post.body.linkUrls.isNotEmpty()) {
                                    loadLinkPreviews(linkPreviewId)
                                }
                            },
                        )
                    },
                    emptyMessage = if (profile.posts.isEmpty()) "表示できる投稿はありません" else null,
                    media = media,
                    mediaEmptyMessage = if (media.isEmpty()) "表示できるメディアはありません" else null,
                )
            }
        }
    }

    private fun createTimelineItems(viewModelState: ViewModelState): List<PostItemUiState> {
        return viewModelState.timeline.posts.map { post ->
            createPostItemUiState(
                iconUrl = post.iconUrl,
                displayName = post.displayName,
                acct = if (post.host == null) "@${post.username}" else "@${post.username}@${post.host}",
                body = post.body,
                linkPreviews = viewModelState.linkPreviews[post.id].orEmpty(),
                sensitiveImagesBlurred = viewModelState.imageBlur.sensitiveImagesBlurred,
                blurToggledImageUrls = viewModelState.imageBlur.toggledImageUrls,
                authorListener = object : PostItemUiState.AuthorListener {
                    override fun onClick() {
                        val host = post.host
                        viewModelScope.launch {
                            events.send { it.navigateToUserProfile(if (host == null) post.username else "${post.username}@$host") }
                        }
                    }
                },
                postListener = object : PostItemUiState.PostListener {
                    override fun onClick() {
                        viewModelScope.launch { events.send { it.navigateToPostDetail(post.id) } }
                    }
                },
                onOpenUrl = { url -> viewModelScope.launch { events.send { it.openUrl(url) } } },
                onOpenImage = { url -> viewModelScope.launch { events.send { it.navigateToImage(url) } } },
                onToggleImageBlur = { url -> toggleImageBlur(url) },
                onShare = { url -> viewModelScope.launch { events.send { it.shareUrl(url) } } },
                onVisible = {
                    if (post.body.linkUrls.isNotEmpty()) {
                        loadLinkPreviews(post.id)
                    }
                },
            )
        }
    }

    private fun createSearchResult(search: SearchState): HomeScreenUiState.SearchResult {
        return when (val result = search.result) {
            SearchResultState.NotSearched -> HomeScreenUiState.SearchResult.NotSearched

            SearchResultState.Loading -> HomeScreenUiState.SearchResult.Loading

            is SearchResultState.Loaded -> HomeScreenUiState.SearchResult.Loaded(
                accounts = result.users.map { user ->
                    createSearchAccount(user, followButtonEnabled = !search.followingChangeInProgress)
                },
                loadMoreButtonVisible = result.nextCursor != null,
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
    private fun TimelineStreamState.status(): HomeScreenUiState.TimelineStreamStatus {
        return when (connection) {
            TimelineStreamConnection.Receiving -> if (catchingUpAfterReconnect) {
                HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect
            } else {
                HomeScreenUiState.TimelineStreamStatus.Receiving
            }

            TimelineStreamConnection.WaitingToReconnect -> HomeScreenUiState.TimelineStreamStatus.WaitingToReconnect

            TimelineStreamConnection.GaveUp,
            TimelineStreamConnection.StoppedByUser,
            -> HomeScreenUiState.TimelineStreamStatus.Disconnected
        }
    }

    internal interface Event {
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

        fun shareUrl(url: String)

        fun navigateToImage(url: String)

        fun navigateToPostDetail(postId: String)
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

    private data class ViewModelState(
        val selectedTab: HomeScreenUiState.Tab,
        val timeline: TimelineState,
        val timelineStream: TimelineStreamState,
        val linkPreviews: Map<String, List<UserLinkPreviewsResult.LinkPreview>>,
        val imageBlur: ImageBlurState,
        val search: SearchState,
        val profile: ProfileState,
        val myPosts: MyPostsState,
        val userContentTab: ProfileContentTab,
    )

    /**
     * @param postsArrivedSinceRefresh 読み直しの結果は要求した時点のものなので、その間に流れてきた投稿を先頭に足し直す
     */
    private data class TimelineState(
        val posts: List<UserHomeTimelineResult.Post>,
        val nextCursor: String?,
        val postsArrivedSinceRefresh: List<UserHomeTimelineResult.Post>,
        val loading: TimelineLoading,
    )

    /**
     * 画面に出ない読み直し（画面表示時・再接続時）は None
     */
    private sealed interface TimelineLoading {
        data object None : TimelineLoading

        data object PullToRefresh : TimelineLoading

        data object Append : TimelineLoading

        /**
         * 失敗した後に自動で読み直すと、末尾にいる間は失敗を繰り返すので、ボタンを押すまで続きを読まない
         */
        data class AppendFailed(
            val message: String,
        ) : TimelineLoading
    }

    private data class TimelineStreamState(
        val connection: TimelineStreamConnection,
        val catchingUpAfterReconnect: Boolean,
    )

    private enum class TimelineStreamConnection {
        Receiving,
        WaitingToReconnect,
        GaveUp,

        /**
         * 自分で切ったときは、画面に戻ってきてもつなぎ直さない
         */
        StoppedByUser,
    }

    /**
     * @param toggledImageUrls 一括で切り替えたら、画像ごとに開け閉めした分は捨てて揃える
     */
    private data class ImageBlurState(
        val sensitiveImagesBlurred: Boolean,
        val toggledImageUrls: Set<String>,
    )

    private data class SearchState(
        val query: String,
        val result: SearchResultState,
        val followingChangeInProgress: Boolean,
    )

    private sealed interface SearchResultState {
        data object NotSearched : SearchResultState

        data object Loading : SearchResultState

        data class Loaded(
            val query: String,
            val users: List<UserSearchResult.User>,
            val nextCursor: String?,
        ) : SearchResultState
    }

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

        /**
         * @param username 再試行で読み直す相手
         */
        data class LoadFailed(
            val username: String,
        ) : MyPostsState
    }
}
