package net.matsudamper.kmp.activitypub.frontend.following

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserCreateFollowingGroupResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserFollowingGroup
import net.matsudamper.kmp.activitypub.frontend.ui.AddFollowingGroupDialogUiState
import net.matsudamper.kmp.activitypub.frontend.ui.FollowMenuUiState

/**
 * 購読ボタンのメニューで、購読・解除・仕分け先の変更と、仕分け先の追加を受け持つ。
 * 購読ボタンを出す画面の ViewModel が持ち、[snapshots] を UiState の組み立てに混ぜる
 */
internal class FollowMenuController(
    private val scope: CoroutineScope,
    private val userApi: UserApi,
    private val showMessage: suspend (String) -> Unit,
) {
    private val stateFlow = MutableStateFlow(
        State(
            changeInProgress = false,
            addGroupTargetActorUri = null,
        ),
    )

    val snapshots: Flow<Snapshot> = combine(stateFlow, userApi.followingGroups) { state, groups ->
        Snapshot(state = state, groups = groups)
    }

    val initialSnapshot: Snapshot get() = Snapshot(state = stateFlow.value, groups = userApi.followingGroups.value)

    private val addGroupDialogListener = object : AddFollowingGroupDialogUiState.Listener {
        override fun onClickAdd(name: String) {
            createGroupAndFollow(name)
        }

        override fun onDismiss() {
            if (stateFlow.value.changeInProgress) return
            stateFlow.update { it.copy(addGroupTargetActorUri = null) }
        }
    }

    init {
        scope.launch { userApi.loadFollowingGroups() }
    }

    /**
     * @param groupId 購読を入れているグループ。購読していないか、ホームなら null
     */
    fun menu(snapshot: Snapshot, actorUri: String, following: Boolean, groupId: String?): FollowMenuUiState {
        return FollowMenuUiState(
            buttonText = when {
                !following -> "購読"
                groupId == null -> HOME_NAME
                else -> snapshot.groups.firstOrNull { it.id == groupId }?.name ?: "購読中"
            } + " ▾",
            buttonEnabled = !snapshot.state.changeInProgress,
            items = buildList {
                if (following) {
                    add(item(text = "購読解除", checked = false) { change(actorUri = actorUri, followed = false, groupId = null) })
                }
                add(item(text = HOME_NAME, checked = following && groupId == null) { change(actorUri = actorUri, followed = true, groupId = null) })
                snapshot.groups.forEach { group ->
                    add(item(text = group.name, checked = following && groupId == group.id) { change(actorUri = actorUri, followed = true, groupId = group.id) })
                }
                add(item(text = "+ 追加", checked = false) { stateFlow.update { it.copy(addGroupTargetActorUri = actorUri) } })
            },
        )
    }

    /**
     * 出していなければ null
     */
    fun addGroupDialog(snapshot: Snapshot): AddFollowingGroupDialogUiState? {
        if (snapshot.state.addGroupTargetActorUri == null) return null
        return AddFollowingGroupDialogUiState(
            inputEnabled = !snapshot.state.changeInProgress,
            listener = addGroupDialogListener,
        )
    }

    private fun item(text: String, checked: Boolean, onClick: () -> Unit): FollowMenuUiState.Item {
        return FollowMenuUiState.Item(
            text = text,
            checkMarkVisible = checked,
            listener = object : FollowMenuUiState.ItemListener {
                override fun onClick() {
                    onClick()
                }
            },
        )
    }

    private fun change(actorUri: String, followed: Boolean, groupId: String?) {
        if (stateFlow.value.changeInProgress) return
        stateFlow.update { it.copy(changeInProgress = true) }
        scope.launch {
            try {
                val error = userApi.setFollowing(actorUri = actorUri, followed = followed, groupId = groupId)
                if (error != null) showMessage(error)
            } finally {
                stateFlow.update { it.copy(changeInProgress = false) }
            }
        }
    }

    private fun createGroupAndFollow(name: String) {
        val state = stateFlow.value
        val actorUri = state.addGroupTargetActorUri ?: return
        if (state.changeInProgress) return
        stateFlow.update { it.copy(changeInProgress = true) }
        scope.launch {
            try {
                when (val result = userApi.createFollowingGroup(name)) {
                    is UserCreateFollowingGroupResult.Failure -> showMessage(result.message)

                    is UserCreateFollowingGroupResult.Created -> {
                        stateFlow.update { it.copy(addGroupTargetActorUri = null) }
                        val error = userApi.setFollowing(actorUri = actorUri, followed = true, groupId = result.group.id)
                        if (error != null) showMessage(error)
                    }
                }
            } finally {
                stateFlow.update { it.copy(changeInProgress = false) }
            }
        }
    }

    data class Snapshot(
        val state: State,
        val groups: List<UserFollowingGroup>,
    )

    /**
     * @param addGroupTargetActorUri 仕分け先の追加ダイアログを出している相手。出していなければ null
     */
    data class State(
        val changeInProgress: Boolean,
        val addGroupTargetActorUri: String?,
    )

    private companion object {
        const val HOME_NAME = "ホーム"
    }
}
