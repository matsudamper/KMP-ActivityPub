package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp

/**
 * タイムラインのカラムやタブの名前と、流す購読先を決めるダイアログ
 */
@Composable
internal fun TimelineColumnEditDialog(uiState: HomeScreenUiState.TimelineColumnEditDialog) {
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text(uiState.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = uiState.name,
                    onValueChange = uiState.listener::onNameChanged,
                    label = { Text("名前") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                SourceCheckboxRow(
                    name = "すべて",
                    checkboxState = uiState.allSourcesCheckState,
                    onClick = uiState.listener::onClickAllSources,
                )
                HorizontalDivider()
                uiState.sources.forEach { source ->
                    SourceCheckboxRow(
                        name = source.name,
                        checkboxState = ToggleableState(source.checked),
                        onClick = source.listener::onClick,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = uiState.listener::onClickSave, enabled = uiState.saveButtonEnabled) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = uiState.listener::onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

@Composable
private fun SourceCheckboxRow(
    name: String,
    checkboxState: ToggleableState,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .triStateToggleable(state = checkboxState, role = Role.Checkbox, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = checkboxState, onClick = null)
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/**
 * カラムやタブを全部消したときに、何も出ていない理由と戻し方を見せる
 */
@Composable
internal fun TimelineColumnsEmptyContent(
    message: String,
    addButtonEnabled: Boolean,
    onClickAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message)
            Button(onClick = onClickAdd, enabled = addButtonEnabled) {
                Text("カラムを追加")
            }
        }
    }
}
