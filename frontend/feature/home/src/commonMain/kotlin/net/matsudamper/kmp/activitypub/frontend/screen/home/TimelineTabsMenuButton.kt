package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * 小さい画面のタブ行の端に置き、押すとタブの追加と、選んでいるタブの購読設定・削除・文字の大きさを選べる
 */
@Composable
internal fun TimelineTabsMenuButton(
    selectedColumn: HomeScreenUiState.TimelineColumn?,
    addItemEnabled: Boolean,
    onClickAdd: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = AppIcons.MoreVert,
                contentDescription = "タブのメニュー",
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            TimelineTabsMenuItems(
                selectedColumn = selectedColumn,
                addItemEnabled = addItemEnabled,
                onClickAdd = onClickAdd,
                onItemClick = { menuExpanded = false },
            )
        }
    }
}

@Composable
internal fun TimelineTabsMenuItems(
    selectedColumn: HomeScreenUiState.TimelineColumn?,
    addItemEnabled: Boolean,
    onClickAdd: () -> Unit,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        DropdownMenuItem(
            text = { Text("追加") },
            leadingIcon = { Icon(imageVector = AppIcons.Add, contentDescription = null) },
            enabled = addItemEnabled,
            onClick = {
                onItemClick()
                onClickAdd()
            },
        )
        if (selectedColumn != null) {
            DropdownMenuItem(
                text = { Text("購読設定") },
                leadingIcon = { Icon(imageVector = AppIcons.Edit, contentDescription = null) },
                onClick = {
                    onItemClick()
                    selectedColumn.listener.onClickEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("削除") },
                leadingIcon = { Icon(imageVector = AppIcons.Delete, contentDescription = null) },
                onClick = {
                    onItemClick()
                    selectedColumn.listener.onClickDelete()
                },
            )
            TimelineColumnFontSizeMenuItem(
                column = selectedColumn,
                onItemClick = onItemClick,
            )
        }
    }
}
