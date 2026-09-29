package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun HomeContentComposePreview() {
    MaterialTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                composeColumn = HomeScreenUiState.ComposeColumn.Compose(
                    acct = "@kotlin@example.com",
                    body = "Kotlin 2.4 がリリースされました",
                    remainingLength = 480,
                    remainingLengthOver = false,
                    submitting = false,
                    error = null,
                    bodyInputEnabled = true,
                    postButtonEnabled = true,
                ),
                listener = AndroidPreviewHomeListener,
            ),
        )
    }
}

@PreviewsMultiSize
@Composable
private fun HomeContentLoginPreview() {
    MaterialTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                composeColumn = HomeScreenUiState.ComposeColumn.Login(
                    username = "kotlin",
                    password = "",
                    submitting = false,
                    error = "ユーザー名かパスワードが違う",
                    inputEnabled = true,
                    loginButtonEnabled = false,
                ),
                listener = AndroidPreviewHomeListener,
            ),
        )
    }
}

private object AndroidPreviewHomeListener : HomeScreenUiState.Listener {
    override fun onClickRetry() = Unit

    override fun onUsernameChanged(text: String) = Unit

    override fun onPasswordChanged(text: String) = Unit

    override fun onClickLogin() = Unit

    override fun onBodyChanged(text: String) = Unit

    override fun onClickPost() = Unit

    override fun onClickLogout() = Unit
}
