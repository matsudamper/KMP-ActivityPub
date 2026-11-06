package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

data class DeleteNoteDialogUiState(
    val deleteButtonEnabled: Boolean,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onClickDelete()

        fun onDismiss()
    }
}
