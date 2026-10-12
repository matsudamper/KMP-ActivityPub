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
                loginButtonEnabled = true,
                listener = AndroidPreviewLoginListener,
            ),
        )
    }
}

private object AndroidPreviewLoginListener : LoginScreenUiState.Listener {
    override fun onUsernameChanged(text: String) = Unit

    override fun onPasswordChanged(text: String) = Unit

    override fun onClickLogin() = Unit
}
