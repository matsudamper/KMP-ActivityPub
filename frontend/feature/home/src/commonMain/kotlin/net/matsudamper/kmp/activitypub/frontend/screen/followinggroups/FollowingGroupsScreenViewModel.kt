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
        combine(api.mainGroupName, api.followingGroups, ::FollowingGroupsState)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                FollowingGroupsState(mainGroupName = api.currentMainGroupName(), groups = api.currentFollowingGroups()),
            )

    private val listener =
        object : FollowingGroupsScreenUiState.Listener {
            override fun onClickBack() {
                viewModelScope.launch {
                    events.send { it.navigateBack() }
                }
            }
        }

    private val editingListener =
        object : FollowingGroupsScreenUiState.EditingListener {
            override fun onClickSave(name: String) {
                rename(name)
            }

            override fun onClickCancel() {
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
                    RenameTarget.MainGroup -> null
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

    private fun item(name: String, target: RenameTarget, viewModelState: ViewModelState): FollowingGroupsScreenUiState.Item {
        return FollowingGroupsScreenUiState.Item(
            name = name,
            state = if (viewModelState.renameTarget == target) {
                FollowingGroupsScreenUiState.ItemState.Editing(
                    inputEnabled = viewModelState.renaming.not(),
                    listener = editingListener,
                )
            } else {
                FollowingGroupsScreenUiState.ItemState.Display(
                    listener = object : FollowingGroupsScreenUiState.DisplayListener {
                        override fun onClickEdit() {
                            // 送っている途中に別の行へ移ると、返事が来たときに移った先の編集まで閉じてしまう
                            if (viewModelStateFlow.value.renaming) return
                            viewModelStateFlow.update { it.copy(renameTarget = target) }
                        }
                    },
                )
            },
        )
    }

    private fun createUiState(
        viewModelState: ViewModelState,
        followingGroups: FollowingGroupsState,
    ): FollowingGroupsScreenUiState {
        return FollowingGroupsScreenUiState(
            mainGroup = item(name = followingGroups.mainGroupName, target = RenameTarget.MainGroup, viewModelState = viewModelState),
            groups = followingGroups.groups.map { group ->
                item(name = group.name, target = RenameTarget.Group(group.id), viewModelState = viewModelState)
            },
            groupsEmptyMessageVisible = followingGroups.groups.isEmpty(),
            listener = listener,
        )
    }

    internal interface Event {
        fun navigateBack()

        fun showMessage(message: String)
    }

    /**
     * @param renameTarget 名前を変えている購読先。変えていなければ null
     */
    private data class ViewModelState(
        val renameTarget: RenameTarget?,
        val renaming: Boolean,
    )

    private data class FollowingGroupsState(
        val mainGroupName: String,
        val groups: List<UserFollowingGroup>,
    )

    private sealed interface RenameTarget {
        data object MainGroup : RenameTarget

        data class Group(val groupId: String) : RenameTarget
    }
}
