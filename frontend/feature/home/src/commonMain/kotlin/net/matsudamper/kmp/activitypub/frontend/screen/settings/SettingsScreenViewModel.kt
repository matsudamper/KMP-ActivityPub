package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import org.koin.core.annotation.KoinViewModel

/**
 * ログアウトはまだサーバーと繋いでいない。ボタンを押しても何も起きない
 */
@KoinViewModel
internal class SettingsScreenViewModel : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()

    val uiStateFlow: StateFlow<SettingsScreenUiState> =
        MutableStateFlow(
            SettingsScreenUiState(
                listener = object : SettingsScreenUiState.Listener {
                    override fun onClickBack() {
                        viewModelScope.launch {
                            events.send { it.navigateBack() }
                        }
                    }

                    override fun onClickLogout() = Unit
                },
            ),
        ).asStateFlow()

    interface Event {
        fun navigateBack()
    }
}
