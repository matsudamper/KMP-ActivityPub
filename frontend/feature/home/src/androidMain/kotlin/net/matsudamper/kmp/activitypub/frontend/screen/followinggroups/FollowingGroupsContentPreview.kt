package net.matsudamper.kmp.activitypub.frontend.screen.followinggroups

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppTheme

@Preview
@Composable
private fun FollowingGroupsContentPreview() {
    MaterialTheme {
        FollowingGroupsContent(uiState = previewFollowingGroupsUiState, snackbarHostState = SnackbarHostState())
    }
}

@Preview
@Composable
private fun FollowingGroupsContentEditingPreview() {
    MaterialTheme {
        FollowingGroupsContent(
            uiState = previewFollowingGroupsUiState.copy(
                groups = previewFollowingGroupsUiState.groups.mapIndexed { index, item ->
                    if (index == 0) item.copy(state = previewEditingState) else item
                },
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun FollowingGroupsContentEmptyDarkPreview() {
    AppTheme(darkTheme = true) {
        FollowingGroupsContent(
            uiState = previewFollowingGroupsUiState.copy(groups = listOf(), groupsEmptyMessageVisible = true),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private val previewDisplayState = FollowingGroupsScreenUiState.ItemState.Display(listener = AndroidPreviewDisplayListener)

private val previewEditingState = FollowingGroupsScreenUiState.ItemState.Editing(
    inputEnabled = true,
    listener = AndroidPreviewEditingListener,
)

private val previewFollowingGroupsUiState = FollowingGroupsScreenUiState(
    home = FollowingGroupsScreenUiState.Item(name = "メイン", state = previewDisplayState),
    groups = listOf("ニュース", "友人", "技術ブログ").map { name ->
        FollowingGroupsScreenUiState.Item(name = name, state = previewDisplayState)
    },
    groupsEmptyMessageVisible = false,
    listener = AndroidPreviewFollowingGroupsListener,
)

private object AndroidPreviewDisplayListener : FollowingGroupsScreenUiState.DisplayListener {
    override fun onClickEdit() = Unit
}

private object AndroidPreviewEditingListener : FollowingGroupsScreenUiState.EditingListener {
    override fun onClickSave(name: String) = Unit

    override fun onClickCancel() = Unit
}

private object AndroidPreviewFollowingGroupsListener : FollowingGroupsScreenUiState.Listener {
    override fun onClickBack() = Unit
}
