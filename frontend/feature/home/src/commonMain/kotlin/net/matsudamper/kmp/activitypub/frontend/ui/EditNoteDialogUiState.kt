package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.runtime.Immutable

/**
 * @param loadingIndicatorVisible 下書きにする本文を読んでいる間 true
 */
data class EditNoteDialogUiState(
    val text: String,
    val textFieldEnabled: Boolean,
    val saveButtonEnabled: Boolean,
    val closeEnabled: Boolean,
    val loadingIndicatorVisible: Boolean,
    val listener: Listener,
) {
    @Immutable
    interface Listener {
        fun onTextChange(text: String)

        fun onClickSave()

        fun onDismiss()
    }
}
