package net.matsudamper.kmp.activitypub.frontend.screen.blockedservers

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppTheme
import net.matsudamper.kmp.activitypub.frontend.ui.UnblockDialogUiState

@Preview
@Composable
private fun BlockedServersContentPreview() {
    MaterialTheme {
        BlockedServersContent(uiState = previewBlockedServersUiState, snackbarHostState = SnackbarHostState())
    }
}

@Preview
@Composable
private fun BlockedServersContentEmptyDarkPreview() {
    AppTheme(darkTheme = true) {
        BlockedServersContent(
            uiState = previewBlockedServersUiState.copy(
                serverInput = "",
                blockButtonEnabled = false,
                content = BlockedServersScreenUiState.Content.Loaded(servers = listOf(), emptyMessageVisible = true),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun BlockedServersContentUnblockDialogPreview() {
    MaterialTheme {
        BlockedServersContent(
            uiState = previewBlockedServersUiState.copy(
                unblockDialog = UnblockDialogUiState(
                    message = "noisy.example のブロックを解除します。このサーバーのユーザーの投稿・ブースト・通知がまた届くようになります",
                    buttonsEnabled = true,
                    listener = AndroidPreviewUnblockDialogListener,
                ),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private val previewBlockedServersUiState = BlockedServersScreenUiState(
    serverInput = "spam.example",
    serverInputEnabled = true,
    blockButtonEnabled = true,
    content = BlockedServersScreenUiState.Content.Loaded(
        servers = listOf(
            BlockedServersScreenUiState.Server(host = "noisy.example", unblockButtonEnabled = true, listener = AndroidPreviewServerListener),
            BlockedServersScreenUiState.Server(host = "very-long-server-name.social.example:8443", unblockButtonEnabled = false, listener = AndroidPreviewServerListener),
        ),
        emptyMessageVisible = false,
    ),
    unblockDialog = null,
    listener = AndroidPreviewBlockedServersListener,
)

private object AndroidPreviewServerListener : BlockedServersScreenUiState.ServerListener {
    override fun onClickUnblock() = Unit
}

private object AndroidPreviewBlockedServersListener : BlockedServersScreenUiState.Listener {
    override fun onClickClose() = Unit

    override fun onChangeServerInput(value: String) = Unit

    override fun onClickBlock() = Unit

    override fun onClickRetry() = Unit
}

private object AndroidPreviewUnblockDialogListener : UnblockDialogUiState.Listener {
    override fun onClickUnblock() = Unit

    override fun onDismiss() = Unit
}
