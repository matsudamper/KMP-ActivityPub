package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * 購読ボタン。押すと購読先を選ぶメニューを開く
 */
@Composable
internal fun FollowMenu(
    uiState: FollowMenuUiState,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        when (val button = uiState.button) {
            is FollowMenuUiState.Button.Follow -> {
                OutlinedButton(
                    onClick = { expanded = true },
                    shape = CircleShape,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    FollowButtonContent(leadingIcon = AppIcons.Add, text = button.text)
                }
            }

            is FollowMenuUiState.Button.Following -> {
                FilledTonalButton(
                    onClick = { expanded = true },
                    shape = CircleShape,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    FollowButtonContent(leadingIcon = AppIcons.Check, text = button.destinationName)
                }
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = FollowMenuShape,
        ) {
            FollowMenuItems(items = uiState.items, onItemClick = { expanded = false })
        }
    }
}

internal val FollowMenuShape = RoundedCornerShape(16.dp)

/**
 * @param onItemClick 項目の listener を呼んだ後に呼ぶ
 */
@Composable
internal fun FollowMenuItems(
    items: List<FollowMenuUiState.Item>,
    onItemClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(IntrinsicSize.Max)) {
        items.forEach { item ->
            if (item is FollowMenuUiState.Item.AddDestination) {
                HorizontalDivider()
            }
            FollowMenuItem(
                item = item,
                onClick = {
                    item.listener.onClick()
                    onItemClick()
                },
            )
            if (item is FollowMenuUiState.Item.Unfollow) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FollowButtonContent(
    leadingIcon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(text = text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(imageVector = AppIcons.ArrowDropDown, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    }
}

@Composable
private fun FollowMenuItem(
    item: FollowMenuUiState.Item,
    onClick: () -> Unit,
) {
    when (item) {
        is FollowMenuUiState.Item.Destination -> {
            DropdownMenuItem(
                text = {
                    Text(
                        text = item.text,
                        fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (item.selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                },
                onClick = onClick,
                leadingIcon = {
                    if (item.selected) {
                        Icon(imageVector = AppIcons.Check, contentDescription = "選択中", tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Spacer(modifier = Modifier.size(24.dp))
                    }
                },
            )
        }

        is FollowMenuUiState.Item.Unfollow -> {
            DropdownMenuItem(
                text = { Text(text = item.text, color = MaterialTheme.colorScheme.error) },
                onClick = onClick,
                leadingIcon = { Icon(imageVector = AppIcons.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            )
        }

        is FollowMenuUiState.Item.AddDestination -> {
            DropdownMenuItem(
                text = { Text(text = item.text, color = MaterialTheme.colorScheme.primary) },
                onClick = onClick,
                leadingIcon = { Icon(imageVector = AppIcons.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            )
        }
    }
}
