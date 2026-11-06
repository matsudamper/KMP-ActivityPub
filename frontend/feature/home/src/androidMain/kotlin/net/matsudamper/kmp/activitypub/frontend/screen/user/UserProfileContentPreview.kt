package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinkUiState

@Preview
@Composable
private fun UserProfileContentPreview() {
    MaterialTheme {
        UserProfileContent(
            uiState = UserProfileScreenUiState(
                content = UserProfileScreenUiState.Content.Loaded(
                    acct = "@alice@example.social",
                    iconUrl = null,
                    displayName = "Alice",
                    biography = "Kotlin と写真が好きです。\n日々のことを投稿しています。",
                    links = listOf(),
                    followButton = UserProfileScreenUiState.FollowButton(text = "フォロー", enabled = true),
                    posts = listOf(
                        PostItemUiState(
                            iconUrl = null,
                            displayName = "Alice",
                            acct = "@alice@example.social",
                            body = createPostBodyText(content = "今日はプロフィール画面を作っています。 https://kotlinlang.org/", onOpenUrl = {}),
                            postedAt = "2026-11-01T12:00:00Z",
                            linkPreviews = listOf(
                                PostItemUiState.LinkPreview(
                                    title = "Kotlin Programming Language",
                                    siteName = "Kotlin",
                                    imageUrl = null,
                                    faviconUrl = null,
                                    listener = AndroidPreviewLinkPreviewListener,
                                ),
                            ),
                            images = listOf(),
                            favouriteCountText = "12",
                            stamps = listOf(PostItemUiState.Stamp(emoji = "❤️", imageUrl = null, countText = "4")),
                            authorListener = null,
                            postListener = null,
                            listener = AndroidPreviewPostItemListener,
                        ),
                    ),
                    postsEmptyMessage = null,
                ),
                listener = AndroidPreviewUserProfileListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun UserProfileContentLocalUserPreview() {
    MaterialTheme {
        UserProfileContent(
            uiState = UserProfileScreenUiState(
                content = UserProfileScreenUiState.Content.Loaded(
                    acct = "@bob@local.example",
                    iconUrl = null,
                    displayName = "Bob",
                    biography = "ローカルのユーザーです。",
                    links = listOf(
                        ProfileLinkUiState(name = "GitHub", url = "https://github.com/bob", listener = AndroidPreviewProfileLinkListener),
                        ProfileLinkUiState(name = "Blog", url = "https://blog.example/bob/very/long/path/to/the/article", listener = AndroidPreviewProfileLinkListener),
                    ),
                    followButton = null,
                    posts = listOf(),
                    postsEmptyMessage = "表示できる投稿はありません",
                ),
                listener = AndroidPreviewUserProfileListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun UserProfileContentNotFoundPreview() {
    MaterialTheme {
        UserProfileContent(
            uiState = UserProfileScreenUiState(
                content = UserProfileScreenUiState.Content.NotFound,
                listener = AndroidPreviewUserProfileListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private object AndroidPreviewUserProfileListener : UserProfileScreenUiState.Listener {
    override fun onClickRetry() = Unit

    override fun onClickBack() = Unit

    override fun onClickFollow() = Unit
}

private object AndroidPreviewPostItemListener : PostItemUiState.Listener {
    override fun onVisible() = Unit
}

private object AndroidPreviewLinkPreviewListener : PostItemUiState.LinkPreviewListener {
    override fun onClick() = Unit
}

private object AndroidPreviewProfileLinkListener : ProfileLinkUiState.Listener {
    override fun onClick() = Unit
}
