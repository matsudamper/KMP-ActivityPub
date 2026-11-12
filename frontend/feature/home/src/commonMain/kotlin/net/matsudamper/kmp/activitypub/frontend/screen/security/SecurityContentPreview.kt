package net.matsudamper.kmp.activitypub.frontend.screen.security

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppTheme

@Preview
@Composable
private fun SecurityContentPreview() {
    MaterialTheme {
        SecurityContent(uiState = previewSecurityUiState, snackbarHostState = SnackbarHostState())
    }
}

@Preview
@Composable
private fun SecurityContentEmptyPreview() {
    MaterialTheme {
        SecurityContent(
            uiState = previewSecurityUiState.copy(
                passkeyList = SecurityScreenUiState.PasskeyList.Empty,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SecurityContentAddDialogPreview() {
    MaterialTheme {
        SecurityContent(
            uiState = previewSecurityUiState.copy(
                addDialog = SecurityScreenUiState.AddDialog(
                    passkeyName = "iPhone",
                    nameInputEnabled = true,
                    addButtonEnabled = true,
                    closeEnabled = true,
                ),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SecurityContentDeleteDialogPreview() {
    MaterialTheme {
        SecurityContent(
            uiState = previewSecurityUiState.copy(
                deleteDialog = SecurityScreenUiState.DeleteDialog(passkeyName = "Pixel", deleteButtonEnabled = true),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SecurityContentUnavailablePreview() {
    MaterialTheme {
        SecurityContent(
            uiState = previewSecurityUiState.copy(
                addPasskeyButtonVisible = false,
                passkeyUnavailableMessageVisible = true,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SecurityContentPasswordDeleteDialogPreview() {
    MaterialTheme {
        SecurityContent(
            uiState = previewSecurityUiState.copy(
                passwordDeleteDialog = SecurityScreenUiState.PasswordDeleteDialog(deleteButtonEnabled = true),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SecurityContentNoPasswordPreview() {
    MaterialTheme {
        SecurityContent(
            uiState = previewSecurityUiState.copy(
                password = SecurityScreenUiState.Password(
                    description = "パスワードは無い。パスキーでログインする",
                    deleteButtonVisible = false,
                    deleteButtonEnabled = false,
                ),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun SecurityContentDarkPreview() {
    AppTheme(darkTheme = true) {
        SecurityContent(uiState = previewSecurityUiState, snackbarHostState = SnackbarHostState())
    }
}

private val previewSecurityUiState = SecurityScreenUiState(
    passkeyList = SecurityScreenUiState.PasskeyList.Items(
        listOf(
            SecurityScreenUiState.PasskeyItem(name = "Pixel", deleteButtonEnabled = true, listener = AndroidPreviewPasskeyItemListener),
            SecurityScreenUiState.PasskeyItem(name = "MacBook", deleteButtonEnabled = true, listener = AndroidPreviewPasskeyItemListener),
        ),
    ),
    addPasskeyButtonVisible = true,
    addPasskeyButtonEnabled = true,
    passkeyUnavailableMessageVisible = false,
    addDialog = null,
    deleteDialog = null,
    password = SecurityScreenUiState.Password(
        description = "パスワードを削除すると、パスキーでしかログインできなくなる",
        deleteButtonVisible = true,
        deleteButtonEnabled = true,
    ),
    passwordDeleteDialog = null,
    listener = AndroidPreviewSecurityListener,
)

private object AndroidPreviewPasskeyItemListener : SecurityScreenUiState.PasskeyItemListener {
    override fun onClickDelete() = Unit
}

private object AndroidPreviewSecurityListener : SecurityScreenUiState.Listener {
    override fun onPasskeyNameChanged(text: String) = Unit

    override fun onClickAddPasskey() = Unit

    override fun onClickConfirmAdd() = Unit

    override fun onDismissAddDialog() = Unit

    override fun onClickConfirmDelete() = Unit

    override fun onDismissDeleteDialog() = Unit

    override fun onClickDeletePassword() = Unit

    override fun onClickConfirmDeletePassword() = Unit

    override fun onDismissDeletePasswordDialog() = Unit

    override fun onClickBack() = Unit
}
