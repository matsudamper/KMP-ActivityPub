package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

data class AddFollowingGroupDialogUiState(
    val inputEnabled: Boolean,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onClickAdd(name: String)

        fun onDismiss()
    }
}
