package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun AdminAccountsContentPreview() {
    MaterialTheme {
        AdminAccountsContent(
            uiState = AdminAccountsScreenUiState(
                content = AdminAccountsScreenUiState.Content.Loaded(
                    accounts = listOf(
                        AdminAccountsScreenUiState.Account(
                            username = "kotlin",
                            acct = "@kotlin@example.com",
                            createdAt = "2026-09-01 10:00",
                        ),
                        AdminAccountsScreenUiState.Account(
                            username = "android",
                            acct = "@android@example.com",
                            createdAt = "2026-09-02 11:00",
                        ),
                    ),
                    loadMoreVisible = true,
                    loadMoreOnVisible = false,
                    loadMoreErrorMessage = null,
                ),
                listener = AndroidPreviewAdminAccountsListener,
            ),
        )
    }
}

private object AndroidPreviewAdminAccountsListener : AdminAccountsScreenUiState.Listener {
    override fun onClickHome() = Unit

    override fun onClickAdmin() = Unit

    override fun onClickNewAccount() = Unit

    override fun onClickReload() = Unit

    override fun onLoadMore() = Unit
}
