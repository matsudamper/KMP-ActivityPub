package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.runtime.Immutable

data class SettingsScreenUiState(
    val themeOptions: List<RadioOption>,
    val linkOpenSectionVisible: Boolean,
    val linkOpenOptions: List<RadioOption>,
    val logoutButtonEnabled: Boolean,
    val adminItemVisible: Boolean,
    val listener: Listener,
) {
    data class RadioOption(
        val label: String,
        val selected: Boolean,
        val listener: RadioOptionListener,
    )

    @Immutable
    interface RadioOptionListener {
        fun onClick()
    }

    @Immutable
    interface Listener {
        fun onClickSecurity()

        fun onClickFollowingGroups()

        fun onClickAdmin()

        fun onClickGitHubRelease()

        fun onClickBack()

        fun onClickLogout()
    }
}
