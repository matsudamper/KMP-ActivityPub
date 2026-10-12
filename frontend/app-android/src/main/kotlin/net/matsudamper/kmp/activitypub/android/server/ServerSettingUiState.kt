package net.matsudamper.kmp.activitypub.android.server

import androidx.compose.runtime.Immutable

data class ServerSettingUiState(
    val serverUrlText: String,
    val dialog: Dialog?,
    val listener: Listener,
) {
    data class Dialog(
        val serverUrl: String,
        val error: String?,
        val saveButtonEnabled: Boolean,
    )

    @Immutable
    interface Listener {
        fun onClickChange()

        fun onServerUrlChanged(text: String)

        fun onClickSave()

        fun onDismissDialog()
    }
}
