package net.matsudamper.kmp.activitypub.frontend.screen.blockedaccounts

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppTheme

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
    listener = AndroidPreviewBlockedAccountsListener,
)

private object AndroidPreviewAccountListener : BlockedAccountsScreenUiState.AccountListener {
    override fun onClickUnblock() = Unit
}

private object AndroidPreviewBlockedAccountsListener : BlockedAccountsScreenUiState.Listener {
    override fun onClickBack() = Unit

    override fun onClickRetry() = Unit
}
