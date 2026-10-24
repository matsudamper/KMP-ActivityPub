package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun ProfileEditContentPreview() {
    MaterialTheme {
        ProfileEditContent(
            uiState = ProfileEditScreenUiState(
                content = ProfileEditScreenUiState.Content.Loaded(
                    displayName = "ユーザー1",
                    displayNameInputEnabled = true,
                    displayNameHasError = false,
                    saveButtonEnabled = true,
                ),
                backButtonEnabled = true,
                listener = AndroidPreviewProfileEditListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun ProfileEditContentErrorPreview() {
    MaterialTheme {
        ProfileEditContent(
            uiState = ProfileEditScreenUiState(
                content = ProfileEditScreenUiState.Content.Error(message = "ネットワークエラー"),
                backButtonEnabled = true,
                listener = AndroidPreviewProfileEditListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private object AndroidPreviewProfileEditListener : ProfileEditScreenUiState.Listener {
    override fun onDisplayNameChanged(text: String) = Unit

    override fun onClickSave() = Unit

    override fun onClickRetry() = Unit

    override fun onClickBack() = Unit
}
