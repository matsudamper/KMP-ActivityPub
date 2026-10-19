package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLogoutResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class SettingsScreenViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                loggingOut = false,
                error = null,
            ),
        )

    val uiStateFlow: StateFlow<SettingsScreenUiState> =
        MutableStateFlow(
            createUiState(
                viewModelState = viewModelStateFlow.value,
                listener =
                object : SettingsScreenUiState.Listener {
                    override fun onClickBack() {
                        viewModelScope.launch {
                            events.send { it.navigateBack() }
                        }
                    }

                    override fun onClickLogout() {
                        logout()
                    }
                },
            ),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                viewModelStateFlow.collect { viewModelState ->
                    uiStateFlow.update { uiState ->
                        createUiState(
                            viewModelState = viewModelState,
                            listener = uiState.listener,
                        )
                    }
                }
            }
        }.asStateFlow()

    private fun logout() {
        if (viewModelStateFlow.value.loggingOut) return

        viewModelStateFlow.update {
            it.copy(
                loggingOut = true,
                error = null,
            )
        }
        viewModelScope.launch {
            val error =
                try {
                    when (val result = api.logout()) {
                        UserLogoutResult.Success -> null
                        is UserLogoutResult.Failure -> result.message
                    }
                } finally {
                    viewModelStateFlow.update { it.copy(loggingOut = false) }
                }
            viewModelStateFlow.update { it.copy(error = error) }
            if (error == null) {
                events.send { it.loggedOut() }
            }
        }
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        listener: SettingsScreenUiState.Listener,
    ): SettingsScreenUiState {
        return SettingsScreenUiState(
            logoutButtonEnabled = viewModelState.loggingOut.not(),
            error = viewModelState.error,
            listener = listener,
        )
    }

    data class ViewModelState(
        val loggingOut: Boolean,
        val error: String?,
    )

    interface Event {
        fun navigateBack()

        fun loggedOut()
    }
}
