package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 自分の投稿の本文を書き換えるダイアログ
 */
@Composable
internal fun EditNoteDialog(uiState: EditNoteDialogUiState) {
    AlertDialog(
        onDismissRequest = {
            if (uiState.closeEnabled) uiState.listener.onDismiss()
        },
        title = { Text("投稿を編集") },
        text = {
            // 読み終えたときに入力欄の位置が動かないよう、読んでいる間も入力欄を出して上に重ねる
            Box(contentAlignment = Alignment.Center) {
                MultilineTextField(
                    value = uiState.text,
                    onValueChange = uiState.listener::onTextChange,
                    label = "本文",
                    inputId = "edit-note-body",
                    inputName = "edit-note-body",
                    enabled = uiState.textFieldEnabled,
                    hasError = false,
                    onSubmitShortcut = uiState.listener::onClickSave,
                    insertion = null,
                    onHandleInsertion = {},
                    modifier = Modifier.fillMaxWidth(),
                )
                if (uiState.loadingIndicatorVisible) {
                    CircularProgressIndicator()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = uiState.listener::onClickSave,
                enabled = uiState.saveButtonEnabled,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                onClick = uiState.listener::onDismiss,
                enabled = uiState.closeEnabled,
            ) {
                Text("キャンセル")
            }
        },
    )
}
