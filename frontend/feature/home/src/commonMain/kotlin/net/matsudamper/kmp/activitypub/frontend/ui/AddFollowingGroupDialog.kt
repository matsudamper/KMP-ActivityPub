package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction

/**
 * 購読の仕分け先を作り、その相手をそこに入れるダイアログ
 */
@Composable
internal fun AddFollowingGroupDialog(uiState: AddFollowingGroupDialogUiState) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text("購読先を追加") },
        text = {
            SingleLineTextField(
                value = name,
                onValueChange = { name = it },
                onImeAction = { uiState.listener.onClickAdd(name) },
                label = "名前",
                kind = SingleLineTextFieldKind.Plain,
                autofill = null,
                formId = null,
                required = true,
                imeAction = ImeAction.Done,
                inputId = "following-group-name",
                inputName = "following-group-name",
                enabled = uiState.inputEnabled,
                hasError = false,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { uiState.listener.onClickAdd(name) },
                enabled = uiState.inputEnabled,
            ) {
                Text("追加")
            }
        },
        dismissButton = {
            TextButton(
                onClick = uiState.listener::onDismiss,
                enabled = uiState.inputEnabled,
            ) {
                Text("キャンセル")
            }
        },
    )
}
