package net.matsudamper.kmp.activitypub.frontend.screen.home

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender

/**
 * サーバーとはまだ繋いでいない。タイムラインは決まったダミーの投稿を出す
 */
internal class HomeScreenViewModel(
    private val viewModelScope: CoroutineScope,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()

    val uiStateFlow: StateFlow<HomeScreenUiState> =
        MutableStateFlow(
            HomeScreenUiState(
                timelineItems = createDummyTimelineItems(),
                listener = object : HomeScreenUiState.Listener {
                    override fun onClickPost() {
                        viewModelScope.launch {
                            events.send { it.navigateToPost() }
                        }
                    }

                    override fun onClickLogout() {
                        viewModelScope.launch {
                            events.send { it.navigateToLogin() }
                        }
                    }
                },
            ),
        ).asStateFlow()

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

        fun navigateToLogin()
    }
}
