package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.runtime.Immutable

data class SettingsScreenUiState(
    val themeOptions: List<ThemeOption>,
    /**
     * 先頭はホーム。続けてグループを作った順
     */
    val followingGroups: List<FollowingGroupItem>,
    /**
     * 出していなければ null
     */
    val renameFollowingGroupDialog: RenameFollowingGroupDialog?,
    val logoutButtonEnabled: Boolean,
    val listener: Listener,
) {
    data class ThemeOption(
        val label: String,
        val selected: Boolean,
        val listener: ThemeOptionListener,
    )

    data class FollowingGroupItem(
        val name: String,
        val listener: FollowingGroupItemListener,
    )

    data class RenameFollowingGroupDialog(
        val initialName: String,
        val inputEnabled: Boolean,
        val listener: RenameFollowingGroupDialogListener,
    )

    @Immutable
    interface FollowingGroupItemListener {
        fun onClick()
    }

    @Immutable
    interface RenameFollowingGroupDialogListener {
        fun onClickSave(name: String)

        fun onDismiss()
    }

    @Immutable
    interface ThemeOptionListener {
        fun onClick()
    }

    @Immutable
    interface Listener {
        fun onClickSecurity()

        fun onClickGitHubRelease()

        fun onClickBack()

        fun onClickLogout()
    }
}
