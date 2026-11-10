package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * タイムラインのカラムやタブに足す購読先を選ぶダイアログ
 */
@Composable
internal fun AddTimelineColumnDialog(uiState: HomeScreenUiState.AddTimelineColumnDialog) {
    AlertDialog(
        onDismissRequest = uiState.listener::onDismiss,
        title = { Text("表示する購読先を追加") },
        text = {
            Column {
                val emptyMessage = uiState.emptyMessage
                if (emptyMessage != null) {
                    Text(emptyMessage)
                }
                uiState.items.forEach { item ->
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = item.listener::onClick)
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = uiState.listener::onDismiss) {
                Text("キャンセル")
            }
        },
    )
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
                Text("購読先を追加")
            }
        }
    }
}
