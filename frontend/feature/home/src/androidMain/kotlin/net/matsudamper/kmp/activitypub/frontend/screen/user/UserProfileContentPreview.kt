package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaUiState

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
                    followButton = UserProfileScreenUiState.FollowButton(text = "フォロー", enabled = true),
                    selectedTab = ProfileContentTab.Posts,
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
                            menuItems = listOf(),
                            postListener = null,
                            listener = AndroidPreviewPostItemListener,
                        ),
                    ),
                    postsEmptyMessage = null,
                    media = listOf(),
                    mediaEmptyMessage = "表示できるメディアはありません",
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
                    biography = null,
                    followButton = null,
                    selectedTab = ProfileContentTab.Posts,
                    posts = listOf(),
                    postsEmptyMessage = "表示できる投稿はありません",
                    media = listOf(),
                    mediaEmptyMessage = "表示できるメディアはありません",
                ),
                listener = AndroidPreviewUserProfileListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview
@Composable
private fun UserProfileContentMediaPreview() {
    MaterialTheme {
        UserProfileContent(
            uiState = UserProfileScreenUiState(
                content = UserProfileScreenUiState.Content.Loaded(
                    acct = "@alice@example.social",
                    iconUrl = null,
                    displayName = "Alice",
                    biography = null,
                    followButton = UserProfileScreenUiState.FollowButton(text = "フォロー", enabled = true),
                    selectedTab = ProfileContentTab.Media,
                    posts = listOf(),
                    postsEmptyMessage = "表示できる投稿はありません",
                    media = List(7) { index ->
                        ProfileMediaUiState(
                            url = "https://example.social/media/$index.png",
                            description = null,
                            blurred = index == 1,
                            listener = AndroidPreviewMediaListener,
                        )
                    },
                    mediaEmptyMessage = null,
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

    override fun onClickTab(tab: ProfileContentTab) = Unit
}

private object AndroidPreviewMediaListener : ProfileMediaUiState.Listener {
    override fun onClick() = Unit
}

private object AndroidPreviewPostItemListener : PostItemUiState.Listener {
    override fun onVisible() = Unit
}

private object AndroidPreviewLinkPreviewListener : PostItemUiState.LinkPreviewListener {
    override fun onClick() = Unit
}
