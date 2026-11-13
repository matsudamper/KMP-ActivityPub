package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * タイムラインのカラムやタブの見出しに置き、押すと購読設定と削除、文字の大きさを選べる
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
                text = { Text("購読設定") },
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
            TimelineColumnFontSizeMenuItem(
                column = column,
                onItemClick = { menuExpanded = false },
            )
        }
    }
}

/**
 * 押すと小・中・大を選ぶメニューを項目の右に開く
 */
@Composable
internal fun TimelineColumnFontSizeMenuItem(
    column: HomeScreenUiState.TimelineColumn,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var subMenuExpanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        DropdownMenuItem(
            text = { Text("文字の大きさ") },
            leadingIcon = { Box(modifier = Modifier.size(24.dp)) },
            trailingIcon = { Icon(imageVector = AppIcons.ArrowRight, contentDescription = null) },
            onClick = { subMenuExpanded = true },
        )
        // DropdownMenu はアンカーの下に開くので、高さ 0 のアンカーを項目の右上に置いて右へ開かせる
        Box(modifier = Modifier.matchParentSize()) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                TimelineColumnFontSizeSubMenu(
                    column = column,
                    expanded = subMenuExpanded,
                    onDismissRequest = { subMenuExpanded = false },
                    onItemClick = {
                        subMenuExpanded = false
                        onItemClick()
                    },
                )
            }
        }
    }
}

@Composable
private fun TimelineColumnFontSizeSubMenu(
    column: HomeScreenUiState.TimelineColumn,
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onItemClick: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
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
