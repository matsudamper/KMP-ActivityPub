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
    private var followingChangeInProgress = false

    private val mutableUiStateFlow: MutableStateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                selectedTab = selectedTab,
                timelineItems = listOf(),
                timelineRefreshing = false,
                timelineLoadMoreVisible = false,
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

                    override fun onLoadMoreTimeline() {
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
        mutableUiStateFlow.update { it.copy(timelineRefreshing = true) }
        timelineJob = viewModelScope.launch {
            try {
                val result = userApi.homeTimeline(cursor)
                currentCoroutineContext().ensureActive()
                when (result) {
                    is UserHomeTimelineResult.Success -> {
                        timelineCursor = result.nextCursor
                        val items = result.posts.map { post ->
                            HomeScreenUiState.TimelineItem(
                                displayName = post.displayName,
                                acct = if (post.host == null) "@${post.username}" else "@${post.username}@${post.host}",
                                body = if (post.sensitive) post.summary ?: "閲覧注意の投稿" else post.content,
                                postedAt = post.publishedAt,
                            )
                        }
                        mutableUiStateFlow.update {
                            it.copy(timelineItems = if (append) it.timelineItems + items else items, timelineLoadMoreVisible = result.nextCursor != null)
                        }
                    }

                    is UserHomeTimelineResult.Failure -> events.send { it.showMessage(result.message) }

                    UserHomeTimelineResult.NotLoggedIn -> events.send { it.showMessage("ログインしていません") }
                }
            } finally {
                if (currentCoroutineContext().isActive) {
                    mutableUiStateFlow.update { it.copy(timelineRefreshing = false) }
                }
            }
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
