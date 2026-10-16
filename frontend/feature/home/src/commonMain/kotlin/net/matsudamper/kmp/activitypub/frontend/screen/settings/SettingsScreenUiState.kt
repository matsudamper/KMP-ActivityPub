package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.runtime.Immutable

data class SettingsScreenUiState(
    val addPasskeyButtonVisible: Boolean,
    val addPasskeyButtonEnabled: Boolean,
    val passkeyMessage: PasskeyMessage?,
    val listener: Listener,
) {
    data class PasskeyMessage(
        val text: String,
        val isError: Boolean,
    )

    @Immutable
    interface Listener {
        fun onClickAddPasskey()

        fun onClickBack()
    }
}
