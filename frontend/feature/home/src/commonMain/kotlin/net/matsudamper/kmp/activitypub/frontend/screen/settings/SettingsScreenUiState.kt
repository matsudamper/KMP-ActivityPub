package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.runtime.Immutable

data class SettingsScreenUiState(
    val logoutButtonEnabled: Boolean,
    val error: String?,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickLogout()
    }
}
