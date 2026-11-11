package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * タイムラインのカラムやタブの見出しに置き、押すと編集と削除を選べる
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
        }
    }
}
