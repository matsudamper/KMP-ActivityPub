package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * 投稿を消す前に確かめるダイアログ
 */
@Composable
internal fun DeleteNoteDialog(uiState: DeleteNoteDialogUiState) {
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text("投稿を削除") },
        text = { Text("投稿と付けた画像を削除する。フォロワーのサーバーにも削除を伝える") },
        confirmButton = {
            TextButton(
                onClick = uiState.listener::onClickDelete,
                enabled = uiState.deleteButtonEnabled,
            ) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(
                onClick = uiState.listener::onDismiss,
                enabled = uiState.deleteButtonEnabled,
            ) {
                Text("キャンセル")
            }
        },
    )
}
