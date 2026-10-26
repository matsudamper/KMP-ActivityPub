package net.matsudamper.kmp.activitypub.frontend.screen.profileedit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun ProfileEditContentPreview() {
    MaterialTheme {
        ProfileEditContent(
            uiState = ProfileEditScreenUiState(
                content = ProfileEditScreenUiState.Content.Loaded(
                    newIcon = null,
                    currentIconUrl = null,
                    iconSelectButtonEnabled = true,
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

@Preview
@Composable
private fun ProfileEditContentNewIconPreview() {
    MaterialTheme {
        ProfileEditContent(
            uiState = ProfileEditScreenUiState(
                content = ProfileEditScreenUiState.Content.Loaded(
                    newIcon = ImageBitmap(width = 72, height = 72).apply {
                        Canvas(this).drawRect(0f, 0f, 72f, 72f, Paint().apply { color = Color(0xFF4A90D9) })
                    },
                    currentIconUrl = null,
                    iconSelectButtonEnabled = true,
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

private object AndroidPreviewProfileEditListener : ProfileEditScreenUiState.Listener {
    override fun onClickSelectIcon() = Unit

    override fun onIconPicked(bytes: ByteArray) = Unit

    override fun onIconTooLarge() = Unit

    override fun onDisplayNameChanged(text: String) = Unit

    override fun onClickSave() = Unit

    override fun onClickRetry() = Unit

    override fun onClickBack() = Unit
}
