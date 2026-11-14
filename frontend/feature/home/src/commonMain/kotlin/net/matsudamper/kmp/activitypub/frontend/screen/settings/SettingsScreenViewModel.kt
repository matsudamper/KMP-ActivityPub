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
import net.matsudamper.kmp.activitypub.frontend.logic.link.LinkOpenMode
import net.matsudamper.kmp.activitypub.frontend.logic.link.LinkOpenSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeMode
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserAdminAccessResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserLogoutResult
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class SettingsScreenViewModel(
    private val api: UserApi,
    private val themeSettingStore: ThemeSettingStore,
    private val linkOpenSettingStore: LinkOpenSettingStore,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                loggingOut = false,
                admin = false,
            ),
        )
    private val themeModeStateFlow: StateFlow<ThemeMode> =
        themeSettingStore.themeModeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)
    private val linkOpenModeStateFlow: StateFlow<LinkOpenMode> =
        linkOpenSettingStore.linkOpenModeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, LinkOpenMode.CustomTab)

    private val listener =
        object : SettingsScreenUiState.Listener {
            override fun onClickSecurity() {
                viewModelScope.launch {
                    events.send { it.navigateToSecurity() }
                }
            }

            override fun onClickFollowingGroups() {
                viewModelScope.launch {
                    events.send { it.navigateToFollowingGroups() }
                }
            }

            override fun onClickBlockedServers() {
                viewModelScope.launch {
                    events.send { it.navigateToBlockedServers() }
                }
            }

            override fun onClickBlockedAccounts() {
                viewModelScope.launch {
                    events.send { it.navigateToBlockedAccounts() }
                }
            }

            override fun onClickAdmin() {
                viewModelScope.launch {
                    events.send { it.navigateToAdmin() }
                }
            }

            override fun onClickGitHubRelease() {
                viewModelScope.launch {
                    events.send { it.openUrl(GITHUB_RELEASES_URL) }
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

    private val themeOptionListeners: Map<ThemeMode, SettingsScreenUiState.RadioOptionListener> =
        ThemeMode.entries.associateWith { themeMode ->
            object : SettingsScreenUiState.RadioOptionListener {
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

    private val linkOpenOptionListeners: Map<LinkOpenMode, SettingsScreenUiState.RadioOptionListener> =
        LinkOpenMode.entries.associateWith { linkOpenMode ->
            object : SettingsScreenUiState.RadioOptionListener {
                override fun onClick() {
                    viewModelScope.launch {
                        val saved = linkOpenSettingStore.save(linkOpenMode)
                        if (saved.not()) {
                            events.send { it.showMessage("リンクの開き方を保存できなかった") }
                        }
                    }
                }
            }
        }

    val uiStateFlow: StateFlow<SettingsScreenUiState> =
        MutableStateFlow(
            createUiState(viewModelStateFlow.value, themeModeStateFlow.value, linkOpenModeStateFlow.value),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                combine(viewModelStateFlow, themeModeStateFlow, linkOpenModeStateFlow, ::createUiState).collect { uiState ->
                    uiStateFlow.value = uiState
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch {
            val admin = api.adminAccess() == UserAdminAccessResult.Granted
            viewModelStateFlow.update { it.copy(admin = admin) }
        }
    }

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
        linkOpenMode: LinkOpenMode,
    ): SettingsScreenUiState {
        return SettingsScreenUiState(
            themeOptions =
            ThemeMode.entries.map { option ->
                SettingsScreenUiState.RadioOption(
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
            linkOpenSectionVisible = linkOpenSettingStore.selectableModes.isNotEmpty(),
            linkOpenOptions =
            linkOpenSettingStore.selectableModes.map { option ->
                SettingsScreenUiState.RadioOption(
                    label =
                    when (option) {
                        LinkOpenMode.ExternalBrowser -> "外部ブラウザ"
                        LinkOpenMode.CustomTab -> "アプリ内（カスタムタブ）"
                    },
                    selected = option == linkOpenMode,
                    listener = linkOpenOptionListeners.getValue(option),
                )
            },
            logoutButtonEnabled = viewModelState.loggingOut.not(),
            adminItemVisible = viewModelState.admin,
            listener = listener,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun navigateToSecurity()

        fun navigateToFollowingGroups()

        fun navigateToBlockedServers()

        fun navigateToBlockedAccounts()

        fun navigateToAdmin()

        fun loggedOut()

        fun showMessage(message: String)

        fun openUrl(url: String)
    }

    private data class ViewModelState(
        val loggingOut: Boolean,
        val admin: Boolean,
    )

    private companion object {
        const val GITHUB_RELEASES_URL = "https://github.com/matsudamper/KMP-ActivityPub/releases"
    }
}
