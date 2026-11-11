package net.matsudamper.kmp.activitypub.frontend.screen.followinggroups

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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFollowingGroup
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
internal class FollowingGroupsScreenViewModel(
    private val api: UserApi,
) : ViewModel() {
    private val events = EventSender<Event>()
    internal val eventHandler = events.asHandler()
    private val viewModelStateFlow: MutableStateFlow<ViewModelState> =
        MutableStateFlow(
            ViewModelState(
                renameTarget = null,
                renaming = false,
            ),
        )
    private val followingGroupsStateFlow: StateFlow<FollowingGroupsState> =
        combine(api.homeGroupName, api.followingGroups, ::FollowingGroupsState)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                FollowingGroupsState(homeName = api.currentHomeGroupName(), groups = api.currentFollowingGroups()),
            )

    private val listener =
        object : FollowingGroupsScreenUiState.Listener {
            override fun onClickBack() {
                viewModelScope.launch {
                    events.send { it.navigateBack() }
                }
            }
        }

    private val renameDialogListener =
        object : FollowingGroupsScreenUiState.RenameDialogListener {
            override fun onClickSave(name: String) {
                rename(name)
            }

            override fun onDismiss() {
                if (viewModelStateFlow.value.renaming) return
                viewModelStateFlow.update { it.copy(renameTarget = null) }
            }
        }

    val uiStateFlow: StateFlow<FollowingGroupsScreenUiState> =
        MutableStateFlow(createUiState(viewModelStateFlow.value, followingGroupsStateFlow.value)).also { uiStateFlow ->
            viewModelScope.launch {
                combine(viewModelStateFlow, followingGroupsStateFlow, ::createUiState).collect { uiState ->
                    uiStateFlow.value = uiState
                }
            }
        }.asStateFlow()

    init {
        viewModelScope.launch { api.loadFollowingGroups() }
    }

    private fun rename(name: String) {
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

    private fun group(name: String, target: RenameTarget): FollowingGroupsScreenUiState.Group {
        return FollowingGroupsScreenUiState.Group(
            name = name,
            listener = object : FollowingGroupsScreenUiState.GroupListener {
                override fun onClick() {
                    viewModelStateFlow.update { it.copy(renameTarget = target) }
                }
            },
        )
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        followingGroups: FollowingGroupsState,
    ): FollowingGroupsScreenUiState {
        val renameTarget = viewModelState.renameTarget
        return FollowingGroupsScreenUiState(
            groups = buildList {
                add(group(name = followingGroups.homeName, target = RenameTarget.Home))
                followingGroups.groups.forEach { group ->
                    add(group(name = group.name, target = RenameTarget.Group(group.id)))
                }
            },
            renameDialog = renameTarget?.let { target ->
                FollowingGroupsScreenUiState.RenameDialog(
                    initialName = when (target) {
                        RenameTarget.Home -> followingGroups.homeName
                        is RenameTarget.Group -> followingGroups.groups.firstOrNull { it.id == target.groupId }?.name.orEmpty()
                    },
                    inputEnabled = viewModelState.renaming.not(),
                    listener = renameDialogListener,
                )
            },
            listener = listener,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    /**
     * @param renameTarget 名前を変えるダイアログを出している購読先。出していなければ null
     */
    private data class ViewModelState(
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
}
