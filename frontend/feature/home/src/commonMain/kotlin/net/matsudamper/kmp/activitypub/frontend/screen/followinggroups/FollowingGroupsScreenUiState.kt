package net.matsudamper.kmp.activitypub.frontend.screen.followinggroups

import androidx.compose.runtime.Immutable

data class FollowingGroupsScreenUiState(
    /**
     * 先頭はホーム。続けてグループを作った順
     */
    val groups: List<Group>,
    /**
     * 出していなければ null
     */
    val renameDialog: RenameDialog?,
    val listener: Listener,
) {
    data class Group(
        val name: String,
        val listener: GroupListener,
    )

    data class RenameDialog(
        val initialName: String,
        val inputEnabled: Boolean,
        val listener: RenameDialogListener,
    )

    @Immutable
    interface GroupListener {
        fun onClick()
    }

    @Immutable
    interface RenameDialogListener {
        fun onClickSave(name: String)

        fun onDismiss()
    }

    @Immutable
    interface Listener {
        fun onClickBack()
    }
}
