package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.runtime.Immutable

data class SettingsScreenUiState(
    val themeOptions: List<ThemeOption>,
    val logoutButtonEnabled: Boolean,
    val adminItemVisible: Boolean,
    val listener: Listener,
) {
    data class ThemeOption(
        val label: String,
        val selected: Boolean,
        val listener: ThemeOptionListener,
    )

    @Immutable
    interface ThemeOptionListener {
        fun onClick()
    }

    @Immutable
    interface Listener {
        fun onClickSecurity()

        fun onClickAdmin()

        fun onClickGitHubRelease()

        fun onClickBack()

        fun onClickLogout()
    }
}
