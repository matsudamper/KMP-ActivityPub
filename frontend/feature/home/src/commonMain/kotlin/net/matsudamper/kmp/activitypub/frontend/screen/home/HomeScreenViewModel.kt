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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserSearchResult
import org.koin.core.annotation.KoinViewModel

/**
 * タイムラインはサーバーとまだ繋いでいないので、決まったダミーを出す
 */
@KoinViewModel
internal class HomeScreenViewModel(
    private val userApi: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val searchStateFlow: MutableStateFlow<SearchState> = MutableStateFlow(SearchState.NotSearched)
    private var searchJob: Job? = null

    private val mutableUiStateFlow: MutableStateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Home,
                timelineItems = createDummyTimelineItems(),
                search = HomeScreenUiState.Search(
                    query = "",
                    result = HomeScreenUiState.SearchResult.NotSearched,
                ),
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
                        mutableUiStateFlow.update { it.copy(selectedTab = tab) }
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
    }

    private fun search(query: String) {
        val trimmedQuery = query.trim()
        searchJob?.cancel()
        if (trimmedQuery.isEmpty()) {
            searchStateFlow.value = SearchState.NotSearched
            return
        }
        searchStateFlow.value = SearchState.Loading
        searchJob = viewModelScope.launch {
            when (val result = userApi.searchUsers(query = trimmedQuery, after = null)) {
                is UserSearchResult.Success -> {
                    searchStateFlow.value = SearchState.Loaded(
                        query = trimmedQuery,
                        usernames = result.usernames,
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

    private fun loadMoreSearchResult() {
        if (searchJob?.isActive == true) return
        val loaded = searchStateFlow.value as? SearchState.Loaded ?: return
        val nextCursor = loaded.nextCursor ?: return
        searchJob = viewModelScope.launch {
            when (val result = userApi.searchUsers(query = loaded.query, after = nextCursor)) {
                is UserSearchResult.Success -> {
                    searchStateFlow.value = loaded.copy(
                        usernames = loaded.usernames + result.usernames,
                        nextCursor = result.nextCursor,
                    )
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
                accounts = searchState.usernames.map { username ->
                    HomeScreenUiState.SearchAccount(
                        avatarInitial = username.take(1).uppercase(),
                        displayName = username,
                        acct = "@$username",
                        listener = object : HomeScreenUiState.SearchAccountListener {
                            override fun onClick() {
                                viewModelScope.launch {
                                    events.send { it.navigateToUserProfile(username) }
                                }
                            }
                        },
                    )
                },
                canLoadMore = searchState.nextCursor != null,
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

    interface Event {
        fun navigateToPost()

        fun navigateToSettings()

        fun navigateToUserProfile(username: String)

        fun showMessage(message: String)
    }

    private sealed interface SearchState {
        data object NotSearched : SearchState

        data object Loading : SearchState

        data class Loaded(
            val query: String,
            val usernames: List<String>,
            val nextCursor: String?,
        ) : SearchState
    }
}
