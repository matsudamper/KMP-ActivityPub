package net.matsudamper.kmp.activitypub.frontend.screen.login

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
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
