package net.matsudamper.kmp.activitypub.frontend.screen.followlist

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler

@Preview
@Composable
private fun FollowListContentPreview() {
    FollowListPreviewTheme {
        FollowListContent(
            uiState = FollowListScreenUiState(
                title = "フォロー",
                content = FollowListScreenUiState.Content.Loaded(
                    accounts = listOf(
                        FollowListScreenUiState.Account(
                            iconUrl = "https://example.com/icon.png",
                            displayName = "Kotlin Weekly",
                            acct = "@kotlinweekly@remote.example",
                            followButtonText = "フォロー中・解除",
                            followButtonEnabled = true,
                            listener = AndroidPreviewAccountListener,
                        ),
                        FollowListScreenUiState.Account(
                            iconUrl = null,
                            displayName = "https://other.example/users/compose",
                            acct = "other.example",
                            followButtonText = "フォロー",
                            followButtonEnabled = true,
                            listener = AndroidPreviewAccountListener,
                        ),
                    ),
                    emptyMessage = null,
                    footer = FollowListScreenUiState.Footer.Error("ネットワークエラー"),
                ),
                listener = AndroidPreviewFollowListListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun FollowListEmptyContentPreview() {
    FollowListPreviewTheme {
        FollowListContent(
            uiState = FollowListScreenUiState(
                title = "フォロワー",
                content = FollowListScreenUiState.Content.Loaded(
                    accounts = listOf(),
                    emptyMessage = "フォロワーはいません",
                    footer = FollowListScreenUiState.Footer.None,
                ),
                listener = AndroidPreviewFollowListListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun FollowListErrorContentPreview() {
    FollowListPreviewTheme {
        FollowListContent(
            uiState = FollowListScreenUiState(
                title = "フォロー",
                content = FollowListScreenUiState.Content.Error("ネットワークエラー"),
                listener = AndroidPreviewFollowListListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@OptIn(ExperimentalCoilApi::class)
@Composable
private fun FollowListPreviewTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { ColorImage(0xFF26A69A.toInt()) },
    ) {
        MaterialTheme(content = content)
    }
}

private object AndroidPreviewFollowListListener : FollowListScreenUiState.Listener {
    override fun onClickBack() = Unit

    override fun onClickRetry() = Unit

    override fun onScrolledToEnd() = Unit

    override fun onClickRetryLoadMore() = Unit
}

private object AndroidPreviewAccountListener : FollowListScreenUiState.AccountListener {
    override fun onClickFollow() = Unit
}
