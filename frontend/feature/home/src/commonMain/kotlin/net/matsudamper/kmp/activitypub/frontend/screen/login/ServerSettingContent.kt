package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

@Composable
internal fun ServerSettingContent(
    uiState: ServerSettingUiState,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "接続先: ${uiState.serverUrlText}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = uiState.listener::onClickChange) {
                Text("変更")
            }
        }
        uiState.dialog?.let { dialog ->
            ServerSettingDialog(dialog = dialog, listener = uiState.listener)
        }
    }
}

@Composable
private fun ServerSettingDialog(
    dialog: ServerSettingUiState.Dialog,
    listener: ServerSettingUiState.Listener,
) {
    AlertDialog(
        onDismissRequest = listener::onDismissDialog,
        title = { Text("接続先のサーバー") },
        text = {
            OutlinedTextField(
                value = dialog.serverUrl,
                onValueChange = listener::onServerUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("https://example.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { listener.onClickSave() }),
                isError = dialog.serverUrlInputError,
            )
        },
        confirmButton = {
            TextButton(
                onClick = listener::onClickSave,
                enabled = dialog.saveButtonEnabled,
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = listener::onDismissDialog) {
                Text("キャンセル")
            }
        },
    )
}
