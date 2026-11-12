package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal fun previewFollowMenu(destinationName: String?): FollowMenuUiState {
    return FollowMenuUiState(
        button = if (destinationName == null) {
            FollowMenuUiState.Button.Follow(text = "購読")
        } else {
            FollowMenuUiState.Button.Following(destinationName = destinationName)
        },
        items = listOf(
            FollowMenuUiState.Item.Unfollow(text = "購読解除", listener = AndroidPreviewFollowMenuItemListener),
            FollowMenuUiState.Item.Destination(text = "メイン", selected = destinationName == "メイン", listener = AndroidPreviewFollowMenuItemListener),
            FollowMenuUiState.Item.Destination(text = "ニュース", selected = destinationName == "ニュース", listener = AndroidPreviewFollowMenuItemListener),
            FollowMenuUiState.Item.AddDestination(text = "購読先を追加", listener = AndroidPreviewFollowMenuItemListener),
        ),
    )
}

@Preview
@Composable
private fun FollowMenuPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FollowMenu(uiState = previewFollowMenu(destinationName = null))
            FollowMenu(uiState = previewFollowMenu(destinationName = "ニュース"))
            Surface(shape = FollowMenuShape, tonalElevation = 3.dp, shadowElevation = 3.dp) {
                FollowMenuItems(items = previewFollowMenu(destinationName = "ニュース").items, onItemClick = {}, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Preview
@Composable
private fun AddFollowingGroupDialogPreview() {
    MaterialTheme {
        AddFollowingGroupDialog(
            uiState = AddFollowingGroupDialogUiState(inputEnabled = true, listener = AndroidPreviewAddFollowingGroupDialogListener),
        )
    }
}

private object AndroidPreviewFollowMenuItemListener : FollowMenuUiState.ItemListener {
    override fun onClick() = Unit
}

private object AndroidPreviewAddFollowingGroupDialogListener : AddFollowingGroupDialogUiState.Listener {
    override fun onClickAdd(name: String) = Unit

    override fun onDismiss() = Unit
}
