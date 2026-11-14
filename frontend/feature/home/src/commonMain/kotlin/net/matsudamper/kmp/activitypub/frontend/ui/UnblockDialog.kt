package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * ブロックを解除する前に確かめるダイアログ
 */
@Composable
internal fun UnblockDialog(uiState: UnblockDialogUiState) {
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text("ブロックを解除") },
        text = { Text(uiState.message) },
        confirmButton = {
            TextButton(
                onClick = uiState.listener::onClickUnblock,
                enabled = uiState.buttonsEnabled,
            ) {
                Text("解除")
            }
        },
        dismissButton = {
            TextButton(
                onClick = uiState.listener::onDismiss,
                enabled = uiState.buttonsEnabled,
            ) {
                Text("キャンセル")
            }
        },
    )
}
