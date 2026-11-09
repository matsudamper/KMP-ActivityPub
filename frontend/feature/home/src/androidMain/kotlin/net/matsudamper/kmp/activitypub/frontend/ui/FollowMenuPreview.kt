package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

internal fun previewFollowMenu(selectedText: String): FollowMenuUiState {
    return FollowMenuUiState(
        selectedText = selectedText,
        enabled = true,
        items = listOf("購読解除", "ホーム", "ニュース", "+ 追加").map { text ->
            FollowMenuUiState.Item(text = text, checkMarkVisible = text == "ホーム", listener = AndroidPreviewFollowMenuItemListener)
        },
    )
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
