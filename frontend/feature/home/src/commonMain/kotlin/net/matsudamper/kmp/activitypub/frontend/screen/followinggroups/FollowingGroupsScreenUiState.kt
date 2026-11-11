package net.matsudamper.kmp.activitypub.frontend.screen.followinggroups

import androidx.compose.runtime.Immutable

data class FollowingGroupsScreenUiState(
    val home: Item,
    /**
     * 作った順
     */
    val groups: List<Item>,
    val groupsEmptyMessageVisible: Boolean,
    val listener: Listener,
) {
    data class Item(
        val name: String,
        val state: ItemState,
    )

    sealed interface ItemState {
        data class Display(
            val listener: DisplayListener,
        ) : ItemState

        data class Editing(
            val inputEnabled: Boolean,
            val listener: EditingListener,
        ) : ItemState
    }

    @Immutable
    interface DisplayListener {
        fun onClickEdit()
    }

    @Immutable
    interface EditingListener {
        fun onClickSave(name: String)

        fun onClickCancel()
    }

    @Immutable
    interface Listener {
        fun onClickBack()
    }
}
