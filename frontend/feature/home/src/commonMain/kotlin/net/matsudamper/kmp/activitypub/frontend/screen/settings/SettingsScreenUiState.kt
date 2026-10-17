package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.runtime.Immutable

data class SettingsScreenUiState(
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onClickBack()

        fun onClickLogout()
    }
}
