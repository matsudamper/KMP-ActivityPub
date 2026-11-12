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
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserCustomEmoji
import net.matsudamper.kmp.activitypub.frontend.screen.createTextWithEmojis
import net.matsudamper.kmp.activitypub.frontend.ui.previewFollowMenu

@Preview
@Composable
private fun FollowListContentPreview() {
    FollowListPreviewTheme {
        FollowListContent(
            uiState = FollowListScreenUiState(
                title = "購読",
                content = FollowListScreenUiState.Content.Loaded(
                    accounts = listOf(
                        FollowListScreenUiState.Account(
                            iconUrl = "https://example.com/icon.png",
                            displayName = createTextWithEmojis(
                                text = "Kotlin Weekly :kotlin:",
                                emojis = listOf(UserCustomEmoji(name = ":kotlin:", imageUrl = "https://example.com/kotlin.png")),
                            ),
                            acct = "@kotlinweekly@remote.example",
                            followMenu = previewFollowMenu(destinationName = "メイン"),
                            clickable = true,
                            listener = AndroidPreviewAccountListener,
                        ),
                        FollowListScreenUiState.Account(
                            iconUrl = null,
                            displayName = createTextWithEmojis(text = "https://other.example/users/compose", emojis = listOf()),
                            acct = "other.example",
                            followMenu = previewFollowMenu(destinationName = null),
                            clickable = false,
                            listener = AndroidPreviewAccountListener,
                        ),
                    ),
                    emptyMessage = null,
                    footer = FollowListScreenUiState.Footer.Error("ネットワークエラー"),
                ),
                addFollowingGroupDialog = null,
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
                addFollowingGroupDialog = null,
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
                title = "購読",
                content = FollowListScreenUiState.Content.Error("ネットワークエラー"),
                addFollowingGroupDialog = null,
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
    override fun onClick() = Unit
}
