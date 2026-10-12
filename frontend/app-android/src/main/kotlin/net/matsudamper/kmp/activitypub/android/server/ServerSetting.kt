package net.matsudamper.kmp.activitypub.android.server

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
internal fun ServerSetting(
    serverSettingStore: ServerSettingStore,
) {
    val viewModelScope = rememberCoroutineScope()
    val viewModel = remember(viewModelScope, serverSettingStore) {
        ServerSettingViewModel(viewModelScope = viewModelScope, serverSettingStore = serverSettingStore)
    }
    val uiState by viewModel.uiStateFlow.collectAsState()

    ServerSettingContent(uiState = uiState)
}

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
            ServerSettingDialogBody(dialog = dialog, listener = listener)
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

@Composable
internal fun ServerSettingDialogBody(
    dialog: ServerSettingUiState.Dialog,
    listener: ServerSettingUiState.Listener,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = dialog.serverUrl,
            onValueChange = listener::onServerUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("https://example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { listener.onClickSave() }),
            isError = dialog.error != null,
        )
        dialog.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
