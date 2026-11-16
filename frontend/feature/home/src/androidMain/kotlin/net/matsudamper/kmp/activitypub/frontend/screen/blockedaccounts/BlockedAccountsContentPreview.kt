package net.matsudamper.kmp.activitypub.frontend.screen.blockedaccounts

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppTheme
import net.matsudamper.kmp.activitypub.frontend.ui.UnblockDialogUiState

@Preview
@Composable
private fun BlockedAccountsContentPreview() {
    MaterialTheme {
        BlockedAccountsContent(uiState = previewBlockedAccountsUiState, snackbarHostState = SnackbarHostState())
    }
}

@Preview
@Composable
private fun BlockedAccountsContentEmptyDarkPreview() {
    AppTheme(darkTheme = true) {
        BlockedAccountsContent(
            uiState = previewBlockedAccountsUiState.copy(
                content = BlockedAccountsScreenUiState.Content.Loaded(accounts = listOf(), emptyMessageVisible = true),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun BlockedAccountsContentUnblockDialogPreview() {
    MaterialTheme {
        BlockedAccountsContent(
            uiState = previewBlockedAccountsUiState.copy(
                unblockDialog = UnblockDialogUiState(
                    message = "@spam@noisy.example のブロックを解除します。このアカウントの投稿・ブースト・通知がまた届くようになります",
                    buttonsEnabled = true,
                    listener = AndroidPreviewUnblockDialogListener,
                ),
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private val previewBlockedAccountsUiState = BlockedAccountsScreenUiState(
    content = BlockedAccountsScreenUiState.Content.Loaded(
        accounts = listOf(
            BlockedAccountsScreenUiState.Account(acct = "@spam@noisy.example", unblockButtonEnabled = true, listener = AndroidPreviewAccountListener),
            BlockedAccountsScreenUiState.Account(
                acct = "@very_long_username_for_preview@very-long-server-name.social.example",
                unblockButtonEnabled = false,
                listener = AndroidPreviewAccountListener,
            ),
        ),
        emptyMessageVisible = false,
    ),
    unblockDialog = null,
    listener = AndroidPreviewBlockedAccountsListener,
)

private object AndroidPreviewAccountListener : BlockedAccountsScreenUiState.AccountListener {
    override fun onClickUnblock() = Unit
}

private object AndroidPreviewBlockedAccountsListener : BlockedAccountsScreenUiState.Listener {
    override fun onClickClose() = Unit

    override fun onClickRetry() = Unit
}

private object AndroidPreviewUnblockDialogListener : UnblockDialogUiState.Listener {
    override fun onClickUnblock() = Unit

    override fun onDismiss() = Unit
}
