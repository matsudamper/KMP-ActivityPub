package net.matsudamper.kmp.activitypub.frontend.screen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.ui.PublicScaffoldListener

class NotFoundScreenViewModel(
    private val viewModelScope: CoroutineScope,
) {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()

    val uiStateFlow: StateFlow<NotFoundScreenUiState> =
        MutableStateFlow(
            NotFoundScreenUiState(
                listener =
                object : NotFoundScreenUiState.Listener {
                    override fun onClickHome() {
                        navigate(Screen.Home)
                    }

                    override fun onClickAdmin() {
                        navigate(Screen.Admin)
                    }
                },
            ),
        ).asStateFlow()

    private fun navigate(screen: Screen) {
        viewModelScope.launch {
            events.send { it.navigate(screen) }
        }
    }

    interface Event {
        suspend fun navigate(screen: Screen)
    }
}

data class NotFoundScreenUiState(
    val listener: Listener,
) {
    interface Listener : PublicScaffoldListener
}
