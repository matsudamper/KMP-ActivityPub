package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import org.koin.core.annotation.KoinViewModel

/**
 * サーバーとはまだ繋いでいない。タイムラインと検索結果は決まったダミーを出す
 */
@KoinViewModel
internal class HomeScreenViewModel : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()

    private val mutableUiStateFlow: MutableStateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                selectedTab = HomeScreenUiState.Tab.Home,
                timelineItems = createDummyTimelineItems(),
                search = HomeScreenUiState.Search(
                    query = "",
                    accounts = listOf(),
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
                        mutableUiStateFlow.update { uiState ->
                            uiState.copy(
                                search = uiState.search.copy(
                                    accounts = searchDummyAccounts(uiState.search.query),
                                ),
                            )
                        }
                    }
                },
            ),
        )
    val uiStateFlow: StateFlow<HomeScreenUiState> = mutableUiStateFlow.asStateFlow()

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

    private fun searchDummyAccounts(query: String): List<HomeScreenUiState.SearchAccount> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return listOf()
        return (1..30)
            .map { index ->
                HomeScreenUiState.SearchAccount(
                    displayName = "ユーザー$index",
                    acct = "@user$index@example.com",
                )
            }.filter { account ->
                account.displayName.contains(trimmedQuery, ignoreCase = true) ||
                    account.acct.contains(trimmedQuery, ignoreCase = true)
            }
    }

    interface Event {
        fun navigateToPost()

        fun navigateToSettings()
    }
}
