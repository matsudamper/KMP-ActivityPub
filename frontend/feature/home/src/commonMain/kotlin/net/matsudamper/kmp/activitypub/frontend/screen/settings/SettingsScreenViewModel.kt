package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.event.EventSender
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeMode
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLogoutResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class SettingsScreenViewModel(
    private val api: UserApi,
    private val themeSettingStore: ThemeSettingStore,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                loggingOut = false,
            ),
        )
    private val themeModeStateFlow: StateFlow<ThemeMode> =
        themeSettingStore.themeModeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)

    private val listener =
        object : SettingsScreenUiState.Listener {
            override fun onClickSecurity() {
                viewModelScope.launch {
                    events.send { it.navigateToSecurity() }
                }
            }

            override fun onClickBack() {
                viewModelScope.launch {
                    events.send { it.navigateBack() }
                }
            }

            override fun onClickLogout() {
                logout()
            }
        }

    private val themeOptionListeners: Map<ThemeMode, SettingsScreenUiState.ThemeOptionListener> =
        ThemeMode.entries.associateWith { themeMode ->
            object : SettingsScreenUiState.ThemeOptionListener {
                override fun onClick() {
                    viewModelScope.launch {
                        val saved = themeSettingStore.save(themeMode)
                        if (saved.not()) {
                            events.send { it.showMessage("テーマを保存できなかった") }
                        }
                    }
                }
            }
        }

    val uiStateFlow: StateFlow<SettingsScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value, themeModeStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                combine(viewModelStateFlow, themeModeStateFlow, ::createUiState).collect { uiState ->
                    uiStateFlow.value = uiState
                }
            }
        }.asStateFlow()

    private fun logout() {
        if (viewModelStateFlow.value.loggingOut) return

        viewModelStateFlow.update { it.copy(loggingOut = true) }
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
            if (error == null) {
                events.send { it.loggedOut() }
            } else {
                events.send { it.showMessage(error) }
            }
        }
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        themeMode: ThemeMode,
    ): SettingsScreenUiState {
        return SettingsScreenUiState(
            themeOptions =
            ThemeMode.entries.map { option ->
                SettingsScreenUiState.ThemeOption(
                    label =
                    when (option) {
                        ThemeMode.System -> "端末の設定に合わせる"
                        ThemeMode.Light -> "ライト"
                        ThemeMode.Dark -> "ダーク"
                    },
                    selected = option == themeMode,
                    listener = themeOptionListeners.getValue(option),
                )
            },
            logoutButtonEnabled = viewModelState.loggingOut.not(),
            listener = listener,
        )
    }

    data class ViewModelState(
        val loggingOut: Boolean,
    )

    interface Event {
        fun navigateBack()

        fun navigateToSecurity()

        fun loggedOut()

        fun showMessage(message: String)
    }
}
