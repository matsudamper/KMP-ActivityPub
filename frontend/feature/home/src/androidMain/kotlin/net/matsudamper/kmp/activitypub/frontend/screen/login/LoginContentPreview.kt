package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun LoginContentPreview() {
    MaterialTheme {
        LoginContent(
            uiState = LoginScreenUiState(
                username = "kotlin",
                password = "password",
                error = null,
                inputHasError = false,
                inputEnabled = true,
                loginButtonEnabled = true,
                listener = AndroidPreviewLoginListener,
            ),
            serverSettingUiState = null,
        )
    }
}

@Preview
@Composable
private fun LoginContentServerSettingPreview() {
    MaterialTheme {
        LoginContent(
            uiState = previewLoginUiState,
            serverSettingUiState = ServerSettingUiState(
                serverUrlText = "https://example.com",
                dialog = null,
                listener = AndroidPreviewServerSettingListener,
            ),
        )
    }
}

@Preview
@Composable
private fun LoginContentServerSettingDialogPreview() {
    MaterialTheme {
        LoginContent(
            uiState = previewLoginUiState,
            serverSettingUiState = ServerSettingUiState(
                serverUrlText = "https://example.com",
                dialog = previewServerSettingDialog,
                listener = AndroidPreviewServerSettingListener,
            ),
        )
    }
}

@Preview
@Composable
private fun LoginContentErrorPreview() {
    MaterialTheme {
        LoginContent(
            uiState = previewLoginUiState.copy(
                error = "ユーザー名かパスワードが違う",
                inputHasError = true,
            ),
            serverSettingUiState = null,
        )
    }
}

private val previewLoginUiState = LoginScreenUiState(
    username = "kotlin",
    password = "password",
    error = null,
    inputHasError = false,
    inputEnabled = true,
    loginButtonEnabled = true,
    listener = AndroidPreviewLoginListener,
)

private val previewServerSettingDialog = ServerSettingUiState.Dialog(
    serverUrl = "example.com/path",
    serverUrlInputError = true,
    saveButtonEnabled = true,
)

private object AndroidPreviewLoginListener : LoginScreenUiState.Listener {
    override fun onUsernameChanged(text: String) = Unit

    override fun onPasswordChanged(text: String) = Unit

    override fun onClickLogin() = Unit
}

private object AndroidPreviewServerSettingListener : ServerSettingUiState.Listener {
    override fun onClickChange() = Unit

    override fun onServerUrlChanged(text: String) = Unit

    override fun onClickSave() = Unit

    override fun onDismissDialog() = Unit
}
