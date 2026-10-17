package net.matsudamper.kmp.activitypub.frontend.screen.logingate

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun LoginGateStatusContentLoadingPreview() {
    MaterialTheme {
        LoginGateStatusContent(
            uiState = LoginGateUiState(
                content = LoginGateUiState.Content.Loading,
                listener = AndroidPreviewLoginGateListener,
            ),
        )
    }
}

@Preview
@Composable
private fun LoginGateStatusContentErrorPreview() {
    MaterialTheme {
        LoginGateStatusContent(
            uiState = LoginGateUiState(
                content = LoginGateUiState.Content.Error(message = "ネットワークエラー"),
                listener = AndroidPreviewLoginGateListener,
            ),
        )
    }
}

private object AndroidPreviewLoginGateListener : LoginGateUiState.Listener {
    override fun onLoggedIn() = Unit

    override fun onClickRetry() = Unit
}
