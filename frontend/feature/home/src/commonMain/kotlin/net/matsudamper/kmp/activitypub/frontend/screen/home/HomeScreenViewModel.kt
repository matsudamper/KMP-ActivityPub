package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFollowingResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserMyProfileResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserResolveResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserSearchResult
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

/**
 * タイムラインはサーバーとまだ繋いでいないので、決まったダミーを出す
 */
@KoinViewModel
internal class HomeScreenViewModel(
    @InjectedParam private val selectedTab: HomeScreenUiState.Tab,
    private val userApi: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val searchStateFlow: MutableStateFlow<SearchState> = MutableStateFlow(SearchState.NotSearched)
    private val followingStateFlow: MutableStateFlow<FollowingState> = MutableStateFlow(FollowingState.Loading)
    private var searchJob: Job? = null
    private var profileJob: Job? = null
    private var followingJob: Job? = null
    private var followingChangeInProgress = false

    private val mutableUiStateFlow: MutableStateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                selectedTab = selectedTab,
                timelineItems = createDummyTimelineItems(),
                timelineRefreshing = false,
                search = HomeScreenUiState.Search(
                    query = "",
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
                profile = HomeScreenUiState.Profile.Loading,
                following = HomeScreenUiState.Following.Loading,
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
                        // 検索タブでフォローを変えてから戻ってきたときも、一覧に反映する
                        if (tab == HomeScreenUiState.Tab.User) {
                            loadFollowing()
                        }
                    }

                    override fun onRefreshTimeline() {
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

                    override fun onClickLoadMoreFollowing() {
                        loadMoreFollowing()
                    }

                    override fun onClickRetryFollowing() {
                        loadFollowing()
                    }

                    override fun onScreenShown() {
                        loadProfile()
                    }
                },
            ),
        )
    val uiStateFlow: StateFlow<HomeScreenUiState> = mutableUiStateFlow.asStateFlow()

    init {
        viewModelScope.launch {
            searchStateFlow.collect { searchState ->
                mutableUiStateFlow.update { uiState ->
                    uiState.copy(search = uiState.search.copy(result = createSearchResult(searchState)))
                }
            }
        }
        viewModelScope.launch {
            followingStateFlow.collect { followingState ->
                mutableUiStateFlow.update { uiState ->
                    uiState.copy(following = createFollowing(followingState))
                }
            }
        }
    }

    private fun loadFollowing() {
        followingJob?.cancel()
        // 出している内容は読み直しの間も残す。タブを切り替えるたびに読み込み中へ切り替わると画面がちらつく
        if (followingStateFlow.value !is FollowingState.Loaded) {
            followingStateFlow.value = FollowingState.Loading
        }
        followingJob = viewModelScope.launch {
            when (val result = userApi.followingUsers(after = null)) {
                is UserFollowingResult.Success -> {
                    followingStateFlow.value = FollowingState.Loaded(
                        users = result.users,
                        unfollowedActorUris = setOf(),
                        nextCursor = result.nextCursor,
                    )
                }

                // ログインしていないことはプロフィールの側で出している
                UserFollowingResult.NotLoggedIn -> {
                    followingStateFlow.value = FollowingState.NotLoaded
                }

                is UserFollowingResult.Failure -> {
                    if (followingStateFlow.value !is FollowingState.Loaded) {
                        followingStateFlow.value = FollowingState.NotLoaded
                    }
                    events.send { it.showMessage(result.message) }
                }
            }
        }
    }

    private fun loadMoreFollowing() {
        if (followingJob?.isActive == true) return
        val loaded = followingStateFlow.value as? FollowingState.Loaded ?: return
        val nextCursor = loaded.nextCursor ?: return
        followingJob = viewModelScope.launch {
            when (val result = userApi.followingUsers(after = nextCursor)) {
                is UserFollowingResult.Success -> {
                    followingStateFlow.update { current ->
                        if (current is FollowingState.Loaded) {
                            current.copy(users = current.users + result.users, nextCursor = result.nextCursor)
                        } else {
                            current
                        }
                    }
                }

                UserFollowingResult.NotLoggedIn -> Unit

                is UserFollowingResult.Failure -> {
                    events.send { it.showMessage(result.message) }
                }
            }
        }
    }

    private fun createFollowing(followingState: FollowingState): HomeScreenUiState.Following {
        return when (followingState) {
            FollowingState.Loading -> HomeScreenUiState.Following.Loading

            FollowingState.NotLoaded -> HomeScreenUiState.Following.NotLoaded

            is FollowingState.Loaded -> HomeScreenUiState.Following.Loaded(
                accounts = followingState.users.map { user ->
                    createFollowingAccount(user = user, following = user.actorUri !in followingState.unfollowedActorUris)
                },
                loadMoreButtonVisible = followingState.nextCursor != null,
            )
        }
    }

    /**
     * 解除しても行は残し、押し間違えたときにその場でフォローし直せるようにする
     */
    private fun createFollowingAccount(
        user: UserFollowingResult.User,
        following: Boolean,
    ): HomeScreenUiState.SearchAccount {
        val username = user.username
        return HomeScreenUiState.SearchAccount(
            followButtonText = if (following) "フォロー中・解除" else "フォロー",
            followButtonEnabled = !followingChangeInProgress,
            onClickFollow = { changeFollowing(actorUri = user.actorUri, followed = !following) },
            iconUrl = user.iconUrl,
            displayName = user.displayName ?: username ?: user.actorUri,
            acct = if (username == null) user.host else "@$username@${user.host}",
            // 他のサーバーのユーザーのプロフィール画面はまだ無い
            listener = null,
        )
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

    private fun createDummyTimelineItems(): List<HomeScreenUiState.TimelineItem> {
        return (1..30).map { index ->
            HomeScreenUiState.TimelineItem(
                displayName = "ユーザー$index",
                acct = "@user$index@example.com",
                body = "ダミーの投稿 $index",
                postedAt = "${index}分前",
            )
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
            onClickFollow = {
                val actorUri = user.actorUri
                if (actorUri != null) {
                    changeFollowing(actorUri = actorUri, followed = !user.following)
                }
            },
            iconUrl = user.iconUrl,
            displayName = user.displayName,
            acct = if (user.isLocal) "@${user.username}" else "@${user.username}@${user.host}",
            // 他のサーバーのユーザーのプロフィール画面はまだ無い
            listener = if (user.isLocal) {
                object : HomeScreenUiState.SearchAccountListener {
                    override fun onClick() {
                        viewModelScope.launch {
                            events.send { it.navigateToUserProfile(user.username) }
                        }
                    }
                }
            } else {
                null
            },
        )
    }

    private fun changeFollowing(
        actorUri: String,
        followed: Boolean,
    ) {
        if (followingChangeInProgress) return
        followingChangeInProgress = true
        refreshFollowButtons()
        viewModelScope.launch {
            try {
                val error = userApi.setFollowing(actorUri, followed = followed)
                if (error == null) {
                    searchStateFlow.update { state ->
                        if (state is SearchState.Loaded) {
                            state.copy(users = state.users.map { if (it.actorUri == actorUri) it.copy(following = followed) else it })
                        } else {
                            state
                        }
                    }
                    followingStateFlow.update { state ->
                        if (state is FollowingState.Loaded) {
                            state.copy(
                                unfollowedActorUris = if (followed) {
                                    state.unfollowedActorUris - actorUri
                                } else {
                                    state.unfollowedActorUris + actorUri
                                },
                            )
                        } else {
                            state
                        }
                    }
                } else {
                    events.send { it.showMessage(error) }
                }
            } finally {
                followingChangeInProgress = false
                refreshFollowButtons()
            }
        }
    }

    private fun refreshFollowButtons() {
        mutableUiStateFlow.update { state ->
            state.copy(
                search = state.search.copy(result = createSearchResult(searchStateFlow.value)),
                following = createFollowing(followingStateFlow.value),
            )
        }
    }

    interface Event {
        fun navigateToTab(tab: HomeScreenUiState.Tab)

        fun navigateToPost()

        fun navigateToSettings()

        fun navigateToUserProfile(username: String)

        fun navigateToProfileEdit()

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

    private sealed interface FollowingState {
        data object Loading : FollowingState

        data object NotLoaded : FollowingState

        /**
         * @param unfollowedActorUris この画面で解除した相手。読み直すまで一覧には残す
         */
        data class Loaded(
            val users: List<UserFollowingResult.User>,
            val unfollowedActorUris: Set<String>,
            val nextCursor: String?,
        ) : FollowingState
    }
}
