package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * @param button 押されたら渡された関数を呼んでメニューを開く
 */
@Composable
internal fun FollowMenu(
    uiState: FollowMenuUiState,
    modifier: Modifier = Modifier,
    button: @Composable (openMenu: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        button { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
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
