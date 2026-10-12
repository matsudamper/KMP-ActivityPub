package net.matsudamper.kmp.activitypub.android.server

import androidx.compose.runtime.Immutable

data class ServerScreenUiState(
    val serverUrl: String,
    val error: String?,
    val connectButtonEnabled: Boolean,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onServerUrlChanged(text: String)

        fun onClickConnect()
    }
}
