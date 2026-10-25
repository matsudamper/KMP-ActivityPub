package net.matsudamper.kmp.activitypub.frontend.screen.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun SettingsContentPreview() {
    MaterialTheme {
        SettingsContent(uiState = previewSettingsUiState, snackbarHostState = SnackbarHostState())
    }
}

@Preview
@Composable
private fun SettingsContentEmptyPreview() {
    MaterialTheme {
        SettingsContent(
            uiState = previewSettingsUiState.copy(
                passkeyList = SettingsScreenUiState.PasskeyList.Empty,
                passkeyName = "",
                addPasskeyButtonEnabled = false,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SettingsContentDeleteDialogPreview() {
    MaterialTheme {
        SettingsContent(
            uiState = previewSettingsUiState.copy(
                deleteDialog = SettingsScreenUiState.DeleteDialog(passkeyName = "Pixel", deleteButtonEnabled = true),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SettingsContentUnavailablePreview() {
    MaterialTheme {
        SettingsContent(
            uiState = previewSettingsUiState.copy(
                passkeyNameInputVisible = false,
                addPasskeyButtonVisible = false,
                passkeyUnavailableMessageVisible = true,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SettingsContentPasswordDeleteDialogPreview() {
    MaterialTheme {
        SettingsContent(
            uiState = previewSettingsUiState.copy(
                passwordDeleteDialog = SettingsScreenUiState.PasswordDeleteDialog(deleteButtonEnabled = true),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SettingsContentNoPasswordPreview() {
    MaterialTheme {
        SettingsContent(
            uiState = previewSettingsUiState.copy(
                password = SettingsScreenUiState.Password(
                    description = "パスワードは無い。パスキーでログインする",
                    deleteButtonVisible = false,
                    deleteButtonEnabled = false,
                ),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private val previewSettingsUiState = SettingsScreenUiState(
    passkeyList = SettingsScreenUiState.PasskeyList.Items(
        listOf(
            SettingsScreenUiState.PasskeyItem(name = "Pixel", deleteButtonEnabled = true, listener = AndroidPreviewPasskeyItemListener),
            SettingsScreenUiState.PasskeyItem(name = "MacBook", deleteButtonEnabled = true, listener = AndroidPreviewPasskeyItemListener),
        ),
    ),
    passkeyNameInputVisible = true,
    passkeyName = "iPhone",
    passkeyNameInputEnabled = true,
    addPasskeyButtonVisible = true,
    addPasskeyButtonEnabled = true,
    passkeyUnavailableMessageVisible = false,
    deleteDialog = null,
    password = SettingsScreenUiState.Password(
        description = "パスワードを削除すると、パスキーでしかログインできなくなる",
        deleteButtonVisible = true,
        deleteButtonEnabled = true,
    ),
    passwordDeleteDialog = null,
    logoutButtonEnabled = true,
    listener = AndroidPreviewSettingsListener,
)

private object AndroidPreviewPasskeyItemListener : SettingsScreenUiState.PasskeyItemListener {
    override fun onClickDelete() = Unit
}

private object AndroidPreviewSettingsListener : SettingsScreenUiState.Listener {
    override fun onPasskeyNameChanged(text: String) = Unit

    override fun onClickAddPasskey() = Unit

    override fun onClickConfirmDelete() = Unit

    override fun onDismissDeleteDialog() = Unit

    override fun onClickDeletePassword() = Unit

    override fun onClickConfirmDeletePassword() = Unit

    override fun onDismissDeletePasswordDialog() = Unit

    override fun onClickBack() = Unit

    override fun onClickLogout() = Unit
}
