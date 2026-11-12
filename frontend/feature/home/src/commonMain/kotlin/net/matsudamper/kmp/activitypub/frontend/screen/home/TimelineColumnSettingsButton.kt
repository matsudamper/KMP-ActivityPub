package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * タイムラインのカラムやタブの見出しに置き、押すと編集と削除、文字の大きさを選べる
 */
@Composable
internal fun TimelineColumnSettingsButton(
    column: HomeScreenUiState.TimelineColumn,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = AppIcons.Settings,
                contentDescription = "${column.title}の設定",
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("編集") },
                leadingIcon = { Icon(imageVector = AppIcons.Edit, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    column.listener.onClickEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("削除") },
                leadingIcon = { Icon(imageVector = AppIcons.Delete, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    column.listener.onClickDelete()
                },
            )
            TimelineColumnFontSizeMenuItems(
                column = column,
                onItemClick = { menuExpanded = false },
            )
        }
    }
}

@Composable
internal fun TimelineColumnFontSizeMenuItems(
    column: HomeScreenUiState.TimelineColumn,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider()
        Text(
            text = "文字の大きさ",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
        column.fontSizeMenuItems.forEach { item ->
            DropdownMenuItem(
                text = { Text(item.label) },
                leadingIcon = {
                    if (item.checked) {
                        Icon(imageVector = AppIcons.Check, contentDescription = "選択中")
                    } else {
                        Box(modifier = Modifier.size(24.dp))
                    }
                },
                onClick = {
                    onItemClick()
                    item.listener.onClick()
                },
            )
        }
    }
}
