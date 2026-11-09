package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * 一覧の行に置くときの幅
 */
internal val FollowMenuWidth = 200.dp

/**
 * 購読先を選ぶプルダウン
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FollowMenu(
    uiState: FollowMenuUiState,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && uiState.enabled },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = uiState.selectedText,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = uiState.enabled,
            label = { Text("購読先") },
            trailingIcon = {
                Icon(
                    imageVector = AppIcons.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = uiState.enabled),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            uiState.items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.text) },
                    onClick = {
                        expanded = false
                        item.listener.onClick()
                    },
                    trailingIcon = {
                        if (item.checkMarkVisible) Text("✓")
                    },
                )
            }
        }
    }
}
