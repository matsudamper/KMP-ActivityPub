package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserHomeTimelineResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLinkPreviewsResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserMyProfileResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserResolveResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserSearchResult
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
    private var profileJob: Job? = null
    private var timelineJob: Job? = null
    private var timelineCursor: String? = null
    private var timelinePosts = listOf<UserHomeTimelineResult.Post>()
    private var linkPreviewRequestedPostIds = setOf<String>()
    private var linkPreviews = mapOf<String, List<UserLinkPreviewsResult.LinkPreview>>()
    private var followingChangeInProgress = false

    private val mutableUiStateFlow: MutableStateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                selectedTab = selectedTab,
                timelineItems = listOf(),
                timelineRefreshing = false,
                timelineFooter = HomeScreenUiState.TimelineFooter.None,
                search = HomeScreenUiState.Search(
                    query = "",
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loading,
                userTabIconUrl = null,
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
                        viewModelScope.launch {
                            events.send { it.navigateToTab(tab) }
                        }
                    }

                    override fun onTabChanged(tab: HomeScreenUiState.Tab) {
                        mutableUiStateFlow.update { it.copy(selectedTab = tab) }
                    }

                    override fun onRefreshTimeline() {
                        loadTimeline(false)
                    }

                    override fun onScrolledToTimelineEnd() {
                        if (mutableUiStateFlow.value.timelineFooter is HomeScreenUiState.TimelineFooter.Error) return
                        loadTimeline(true)
                    }

                    override fun onClickRetryTimelineLoadMore() {
                        loadTimeline(true)
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
                        loadTimeline(false)
                    }
                },
            ),
        )
    val uiStateFlow: StateFlow<HomeScreenUiState> = mutableUiStateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            userApi.followingChanges.collect { change ->
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
            val profile =
                when (val result = userApi.myProfile()) {
                    is UserMyProfileResult.Found -> HomeScreenUiState.Profile.Loaded(
                        iconUrl = result.iconUrl,
                        displayName = result.displayName ?: result.username,
                        acct = "@${result.username}",
                        followingCount = result.followingCount.toString(),
                        followersCount = result.followersCount.toString(),
                    )

                    UserMyProfileResult.NotLoggedIn -> HomeScreenUiState.Profile.Error("ログインしていません")

                    is UserMyProfileResult.Failure -> HomeScreenUiState.Profile.Error(result.message)
                }
            mutableUiStateFlow.update {
                it.copy(
                    profile = profile,
                    userTabIconUrl = (profile as? HomeScreenUiState.Profile.Loaded)?.iconUrl,
                )
            }
        }
    }

    private fun search(query: String) {
        val trimmedQuery = query.trim()
        searchJob?.cancel()
        if (trimmedQuery.isEmpty()) {
            searchStateFlow.value = SearchState.NotSearched
            return
        }
        searchStateFlow.value = SearchState.Loading
        if (isResolveQuery(trimmedQuery)) {
            searchJob = viewModelScope.launch { resolveUser(trimmedQuery) }
            return
        }
        searchJob = viewModelScope.launch {
            when (val result = userApi.searchUsers(query = trimmedQuery, after = null)) {
                is UserSearchResult.Success -> {
                    searchStateFlow.value = SearchState.Loaded(
                        query = trimmedQuery,
                        users = result.users,
                        nextCursor = result.nextCursor,
                    )
                }

                is UserSearchResult.Failure -> {
                    searchStateFlow.value = SearchState.NotSearched
                    events.send { it.showMessage(result.message) }
                }
            }
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
        val loaded = searchStateFlow.value as? SearchState.Loaded ?: return
        val nextCursor = loaded.nextCursor ?: return
        searchJob = viewModelScope.launch {
            when (val result = userApi.searchUsers(query = loaded.query, after = nextCursor)) {
                is UserSearchResult.Success -> {
                    searchStateFlow.update { current ->
                        if (current is SearchState.Loaded && current.query == loaded.query) {
                            current.copy(
                                users = current.users + result.users,
                                nextCursor = result.nextCursor,
                            )
                        } else {
                            current
                        }
                    }
                }

                is UserSearchResult.Failure -> {
                    events.send { it.showMessage(result.message) }
                }
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

    private fun loadTimeline(append: Boolean) {
        if (append && timelineJob?.isActive == true) return
        val cursor = if (append) timelineCursor ?: return else null
        timelineJob?.cancel()
        mutableUiStateFlow.update {
            it.copy(
                timelineRefreshing = !append,
                timelineFooter = if (append) HomeScreenUiState.TimelineFooter.Loading else HomeScreenUiState.TimelineFooter.None,
            )
        }
        timelineJob = viewModelScope.launch {
            try {
                val result = userApi.homeTimeline(cursor)
                currentCoroutineContext().ensureActive()
                when (result) {
                    is UserHomeTimelineResult.Success -> {
                        timelineCursor = result.nextCursor
                        timelinePosts = (if (append) timelinePosts + result.posts else result.posts).distinctBy { it.id }
                        mutableUiStateFlow.update {
                            it.copy(timelineItems = createTimelineItems(), timelineFooter = HomeScreenUiState.TimelineFooter.None)
                        }
                    }

                    is UserHomeTimelineResult.Failure -> timelineFailed(append, result.message)

                    UserHomeTimelineResult.NotLoggedIn -> timelineFailed(append, "ログインしていません")
                }
            } finally {
                if (currentCoroutineContext().isActive) {
                    mutableUiStateFlow.update { it.copy(timelineRefreshing = false) }
                }
            }
        }
    }

    private fun createTimelineItems(): List<HomeScreenUiState.TimelineItem> {
        return timelinePosts.map { post ->
            val previews = linkPreviews[post.id].orEmpty()
            HomeScreenUiState.TimelineItem(
                displayName = post.displayName,
                acct = if (post.host == null) "@${post.username}" else "@${post.username}@${post.host}",
                body = if (post.sensitive) post.summary ?: "閲覧注意の投稿" else post.content,
                postedAt = post.publishedAt,
                // 閲覧注意の投稿は本文を隠しているので、リンク先も出さない
                linkPreviews = if (post.sensitive) {
                    listOf()
                } else {
                    post.linkUrls.map { url -> createLinkPreview(url = url, preview = previews.firstOrNull { it.url == url }) }
                },
                listener = object : HomeScreenUiState.TimelineItemListener {
                    override fun onVisible() {
                        if (post.sensitive.not() && post.linkUrls.isNotEmpty()) {
                            loadLinkPreviews(post.id)
                        }
                    }
                },
            )
        }
    }

    /**
     * @param preview まだ取れていなければ null。枠は先に出しておき、取れたら中身だけ差し替える
     */
    private fun createLinkPreview(
        url: String,
        preview: UserLinkPreviewsResult.LinkPreview?,
    ): HomeScreenUiState.LinkPreview {
        return HomeScreenUiState.LinkPreview(
            title = preview?.title ?: url,
            siteName = preview?.siteName ?: url.substringAfter("://").substringBefore('/'),
            imageUrl = preview?.imageUrl,
            listener = object : HomeScreenUiState.LinkPreviewListener {
                override fun onClick() {
                    viewModelScope.launch {
                        events.send { it.openUrl(url) }
                    }
                }
            },
        )
    }

    private fun loadLinkPreviews(postId: String) {
        if (postId in linkPreviewRequestedPostIds) return
        linkPreviewRequestedPostIds = linkPreviewRequestedPostIds + postId
        viewModelScope.launch {
            when (val result = userApi.linkPreviews(postId)) {
                is UserLinkPreviewsResult.Success -> {
                    linkPreviews = linkPreviews + (postId to result.previews)
                    mutableUiStateFlow.update { it.copy(timelineItems = createTimelineItems()) }
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

        fun navigateToPost()

        fun navigateToSettings()

        fun navigateToUserProfile(username: String)

        fun navigateToProfileEdit()

        fun navigateToFollowing()

        fun navigateToFollowers()

        fun showMessage(message: String)

        fun openUrl(url: String)
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
