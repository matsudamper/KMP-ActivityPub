package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun EditNoteDialogPreview() {
    MaterialTheme {
        EditNoteDialog(
            uiState = EditNoteDialogUiState(
                text = "誤字を直した本文です\nhttps://example.com/",
                textFieldEnabled = true,
                saveButtonEnabled = true,
                closeEnabled = true,
                loadingIndicatorVisible = false,
                listener = AndroidPreviewEditNoteDialogListener,
            ),
        )
    }
}

@Preview
@Composable
private fun EditNoteDialogLoadingPreview() {
    MaterialTheme {
        EditNoteDialog(
            uiState = EditNoteDialogUiState(
                text = "",
                textFieldEnabled = false,
                saveButtonEnabled = false,
                closeEnabled = true,
                loadingIndicatorVisible = true,
                listener = AndroidPreviewEditNoteDialogListener,
            ),
        )
    }
}

private object AndroidPreviewEditNoteDialogListener : EditNoteDialogUiState.Listener {
    override fun onTextChange(text: String) = Unit

    override fun onClickSave() = Unit

    override fun onDismiss() = Unit
}
