package net.matsudamper.kmp.activitypub.frontend.screen.followinggroups

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun FollowingGroupsContentPreview() {
    MaterialTheme {
        FollowingGroupsContent(uiState = previewFollowingGroupsUiState, snackbarHostState = SnackbarHostState())
    }
}

@Preview
@Composable
private fun FollowingGroupsContentRenameDialogPreview() {
    MaterialTheme {
        FollowingGroupsContent(
            uiState = previewFollowingGroupsUiState.copy(
                renameDialog = FollowingGroupsScreenUiState.RenameDialog(
                    initialName = "ニュース",
                    inputEnabled = true,
                    listener = AndroidPreviewRenameDialogListener,
                ),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private val previewFollowingGroupsUiState = FollowingGroupsScreenUiState(
    groups = listOf("ホーム", "ニュース", "友人").map { name ->
        FollowingGroupsScreenUiState.Group(name = name, listener = AndroidPreviewGroupListener)
    },
    renameDialog = null,
    listener = AndroidPreviewFollowingGroupsListener,
)

private object AndroidPreviewGroupListener : FollowingGroupsScreenUiState.GroupListener {
    override fun onClick() = Unit
}

private object AndroidPreviewRenameDialogListener : FollowingGroupsScreenUiState.RenameDialogListener {
    override fun onClickSave(name: String) = Unit

    override fun onDismiss() = Unit
}

private object AndroidPreviewFollowingGroupsListener : FollowingGroupsScreenUiState.Listener {
    override fun onClickBack() = Unit
}
