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
            knownStates = mapOf(),
            sendingActorUris = setOf(),
            groupCreating = false,
            addGroupTargetActorUri = null,
        ),
    )

    val snapshots: Flow<Snapshot> = combine(stateFlow, userApi.followingGroups) { state, groups ->
        Snapshot(state = state, groups = groups)
    }

    val initialSnapshot: Snapshot get() = Snapshot(state = stateFlow.value, groups = userApi.currentFollowingGroups())

    private val addGroupDialogListener = object : AddFollowingGroupDialogUiState.Listener {
        override fun onClickAdd(name: String) {
            createGroupAndFollow(name)
        }

        override fun onDismiss() {
            if (stateFlow.value.groupCreating) return
            stateFlow.update { it.copy(addGroupTargetActorUri = null) }
        }
    }

    init {
        scope.launch { userApi.loadFollowingGroups() }
        scope.launch {
            // 画面のデータは受け取ってから書き換わるまで間があるので、届いた変更をここでも覚えて先に出す
            userApi.followingChanges.collect { change ->
                rememberState(actorUri = change.actorUri, state = KnownState(following = change.following, groupId = change.groupId))
            }
        }
    }

    /**
     * 送った変更は返事や画面のデータの書き換えを待たずに出す。待つ間にボタンが一度戻ったり押せなくなったりしてちらつかないようにする
     *
     * @param receivedFollowing 画面が受け取っている購読状態
     * @param receivedGroupId 画面が受け取っている購読先のグループ。購読していないか、ホームなら null
     */
    fun menu(snapshot: Snapshot, actorUri: String, receivedFollowing: Boolean, receivedGroupId: String?): FollowMenuUiState {
        val known = snapshot.state.knownStates[actorUri]
        val following = known?.following ?: receivedFollowing
        val groupId = if (known == null) receivedGroupId else known.groupId
        return FollowMenuUiState(
            button = when {
                !following -> FollowMenuUiState.Button.Follow(text = "購読")
                groupId == null -> FollowMenuUiState.Button.Following(destinationName = HOME_NAME)
                else -> FollowMenuUiState.Button.Following(destinationName = snapshot.groups.firstOrNull { it.id == groupId }?.name ?: "購読中")
            },
            items = buildList {
                if (following) {
                    add(FollowMenuUiState.Item.Unfollow(text = "購読解除", listener = itemListener { change(actorUri = actorUri, followed = false, groupId = null) }))
                }
                add(destination(text = HOME_NAME, selected = following && groupId == null) { change(actorUri = actorUri, followed = true, groupId = null) })
                snapshot.groups.forEach { group ->
                    add(destination(text = group.name, selected = following && groupId == group.id) { change(actorUri = actorUri, followed = true, groupId = group.id) })
                }
                add(FollowMenuUiState.Item.AddDestination(text = "購読先を追加", listener = itemListener { stateFlow.update { it.copy(addGroupTargetActorUri = actorUri) } }))
            },
        )
    }

    /**
     * 出していなければ null
     */
    fun addGroupDialog(snapshot: Snapshot): AddFollowingGroupDialogUiState? {
        if (snapshot.state.addGroupTargetActorUri == null) return null
        return AddFollowingGroupDialogUiState(
            inputEnabled = !snapshot.state.groupCreating,
            listener = addGroupDialogListener,
        )
    }

    private fun destination(text: String, selected: Boolean, onClick: () -> Unit): FollowMenuUiState.Item {
        return FollowMenuUiState.Item.Destination(text = text, selected = selected, listener = itemListener(onClick))
    }

    private fun itemListener(onClick: () -> Unit): FollowMenuUiState.ItemListener {
        return object : FollowMenuUiState.ItemListener {
            override fun onClick() {
                onClick()
            }
        }
    }

    private fun change(actorUri: String, followed: Boolean, groupId: String?) {
        // 送っている途中に重ねて送ると、返事の順で状態が入れ替わることがあるので受け付けない
        if (actorUri in stateFlow.value.sendingActorUris) return
        scope.launch { send(actorUri = actorUri, followed = followed, groupId = groupId) }
    }

    /**
     * 失敗したら、送る前に覚えていた状態に戻す
     */
    private suspend fun send(actorUri: String, followed: Boolean, groupId: String?) {
        val before = stateFlow.value.knownStates[actorUri]
        stateFlow.update { it.copy(sendingActorUris = it.sendingActorUris + actorUri) }
        rememberState(actorUri = actorUri, state = KnownState(following = followed, groupId = groupId))
        try {
            val error = userApi.setFollowing(actorUri = actorUri, followed = followed, groupId = groupId)
            if (error != null) {
                stateFlow.update { state ->
                    val knownStates = if (before == null) state.knownStates - actorUri else state.knownStates + (actorUri to before)
                    state.copy(knownStates = knownStates)
                }
                showMessage(error)
            }
        } finally {
            stateFlow.update { it.copy(sendingActorUris = it.sendingActorUris - actorUri) }
        }
    }

    private fun rememberState(actorUri: String, state: KnownState) {
        stateFlow.update { it.copy(knownStates = it.knownStates + (actorUri to state)) }
    }

    private fun createGroupAndFollow(name: String) {
        val state = stateFlow.value
        val actorUri = state.addGroupTargetActorUri ?: return
        if (state.groupCreating) return
        stateFlow.update { it.copy(groupCreating = true) }
        scope.launch {
            try {
                when (val result = userApi.createFollowingGroup(name)) {
                    is UserCreateFollowingGroupResult.Failure -> showMessage(result.message)

                    is UserCreateFollowingGroupResult.Created -> {
                        stateFlow.update { it.copy(addGroupTargetActorUri = null, groupCreating = false) }
                        if (actorUri !in stateFlow.value.sendingActorUris) {
                            send(actorUri = actorUri, followed = true, groupId = result.group.id)
                        }
                    }
                }
            } finally {
                stateFlow.update { it.copy(groupCreating = false) }
            }
        }
    }

    data class Snapshot(
        val state: State,
        val groups: List<UserFollowingGroup>,
    )

    /**
     * @param knownStates この画面を開いてから送ったか届いた購読状態。相手の actor URI ごと。画面のデータより新しい
     * @param sendingActorUris 変更を送っている途中の相手
     * @param addGroupTargetActorUri 購読先の追加ダイアログを出している相手。出していなければ null
     */
    data class State(
        val knownStates: Map<String, KnownState>,
        val sendingActorUris: Set<String>,
        val groupCreating: Boolean,
        val addGroupTargetActorUri: String?,
    )

    /**
     * @param groupId 購読先のグループ。購読していないか、ホームなら null
     */
    data class KnownState(
        val following: Boolean,
        val groupId: String?,
    )

    private companion object {
        const val HOME_NAME = "ホーム"
    }
}
