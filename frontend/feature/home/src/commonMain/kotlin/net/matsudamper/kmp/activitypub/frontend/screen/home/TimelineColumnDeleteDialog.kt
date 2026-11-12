package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
internal fun TimelineColumnDeleteDialog(uiState: HomeScreenUiState.TimelineColumnDeleteDialog) {
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text("カラムを削除") },
        text = { Text(uiState.message) },
        confirmButton = {
            TextButton(onClick = uiState.listener::onClickDelete) {
                Text("削除")
            }
        },
        dismissButton = {
            TextButton(onClick = uiState.listener::onDismiss) {
                Text("キャンセル")
            }
        },
    )
}
