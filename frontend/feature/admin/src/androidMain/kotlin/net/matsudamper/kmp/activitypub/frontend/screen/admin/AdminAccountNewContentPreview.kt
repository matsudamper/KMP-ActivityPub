package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AdminAccountNewContentPreview() {
    MaterialTheme {
        AdminAccountNewContent(
            uiState = AdminAccountNewScreenUiState(
                content = AdminAccountNewScreenUiState.Content.Input(
                    username = "kotlin",
                    password = "password",
                    submitting = false,
                    error = null,
                    inputEnabled = true,
                    addButtonEnabled = true,
                ),
                listener = AndroidPreviewAdminAccountNewListener,
            ),
        )
    }
}

private object AndroidPreviewAdminAccountNewListener : AdminAccountNewScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onUsernameChanged(text: String) = Unit

    override fun onPasswordChanged(text: String) = Unit

    override fun onClickAdd() = Unit
}
