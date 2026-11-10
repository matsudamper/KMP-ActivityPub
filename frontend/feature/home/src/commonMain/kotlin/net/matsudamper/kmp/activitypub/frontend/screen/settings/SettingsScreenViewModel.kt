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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFollowingGroup
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
                renameTarget = null,
                renaming = false,
            ),
        )
    private val themeModeStateFlow: StateFlow<ThemeMode> =
        themeSettingStore.themeModeFlow.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)
    private val followingGroupsStateFlow: StateFlow<FollowingGroupsState> =
        combine(api.homeGroupName, api.followingGroups, ::FollowingGroupsState)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                FollowingGroupsState(homeName = api.currentHomeGroupName(), groups = api.currentFollowingGroups()),
            )

    private val listener =
        object : SettingsScreenUiState.Listener {
            override fun onClickSecurity() {
                viewModelScope.launch {
                    events.send { it.navigateToSecurity() }
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

    private val renameDialogListener =
        object : SettingsScreenUiState.RenameFollowingGroupDialogListener {
            override fun onClickSave(name: String) {
                renameFollowingGroup(name)
            }

            override fun onDismiss() {
                if (viewModelStateFlow.value.renaming) return
                viewModelStateFlow.update { it.copy(renameTarget = null) }
            }
        }

    val uiStateFlow: StateFlow<SettingsScreenUiState> =
        MutableStateFlow(
            createUiState(viewModelStateFlow.value, themeModeStateFlow.value, followingGroupsStateFlow.value),
        ).also { uiStateFlow ->
            viewModelScope.launch {
                combine(viewModelStateFlow, themeModeStateFlow, followingGroupsStateFlow, ::createUiState).collect { uiState ->
                    uiStateFlow.value = uiState
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch { api.loadFollowingGroups() }
    }

    private fun renameFollowingGroup(name: String) {
        val state = viewModelStateFlow.value
        val target = state.renameTarget ?: return
        if (state.renaming) return

        viewModelStateFlow.update { it.copy(renaming = true) }
        viewModelScope.launch {
            val groupId =
                when (target) {
                    RenameTarget.Home -> null
                    is RenameTarget.Group -> target.groupId
                }
            val error =
                try {
                    api.renameFollowingGroup(groupId = groupId, name = name)
                } finally {
                    viewModelStateFlow.update { it.copy(renaming = false) }
                }
            if (error == null) {
                viewModelStateFlow.update { it.copy(renameTarget = null) }
            } else {
                events.send { it.showMessage(error) }
            }
        }
    }

    private fun followingGroupItem(name: String, target: RenameTarget): SettingsScreenUiState.FollowingGroupItem {
        return SettingsScreenUiState.FollowingGroupItem(
            name = name,
            listener = object : SettingsScreenUiState.FollowingGroupItemListener {
                override fun onClick() {
                    viewModelStateFlow.update { it.copy(renameTarget = target) }
                }
            },
        )
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
        followingGroups: FollowingGroupsState,
    ): SettingsScreenUiState {
        val renameTarget = viewModelState.renameTarget
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
            followingGroups = buildList {
                add(followingGroupItem(name = followingGroups.homeName, target = RenameTarget.Home))
                followingGroups.groups.forEach { group ->
                    add(followingGroupItem(name = group.name, target = RenameTarget.Group(group.id)))
                }
            },
            renameFollowingGroupDialog = renameTarget?.let { target ->
                SettingsScreenUiState.RenameFollowingGroupDialog(
                    initialName = when (target) {
                        RenameTarget.Home -> followingGroups.homeName
                        is RenameTarget.Group -> followingGroups.groups.firstOrNull { it.id == target.groupId }?.name.orEmpty()
                    },
                    inputEnabled = viewModelState.renaming.not(),
                    listener = renameDialogListener,
                )
            },
            logoutButtonEnabled = viewModelState.loggingOut.not(),
            listener = listener,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun navigateToSecurity()

        fun loggedOut()

        fun showMessage(message: String)

        fun openUrl(url: String)
    }

    /**
     * @param renameTarget 名前を変えるダイアログを出している購読先。出していなければ null
     */
    private data class ViewModelState(
        val loggingOut: Boolean,
        val renameTarget: RenameTarget?,
        val renaming: Boolean,
    )

    private data class FollowingGroupsState(
        val homeName: String,
        val groups: List<UserFollowingGroup>,
    )

    private sealed interface RenameTarget {
        data object Home : RenameTarget

        data class Group(val groupId: String) : RenameTarget
    }

    private companion object {
        const val GITHUB_RELEASES_URL = "https://github.com/matsudamper/KMP-ActivityPub/releases"
    }
}
