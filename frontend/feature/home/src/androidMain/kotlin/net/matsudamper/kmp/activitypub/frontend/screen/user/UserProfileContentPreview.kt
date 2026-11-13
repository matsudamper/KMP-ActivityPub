package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import net.matsudamper.kmp.activitypub.frontend.screen.UserProfileDialogFrame
import net.matsudamper.kmp.activitypub.frontend.screen.createPostBodyText
import net.matsudamper.kmp.activitypub.frontend.screen.createTextWithEmojis
import net.matsudamper.kmp.activitypub.frontend.ui.PostItemUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileContentTab
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileLinkUiState
import net.matsudamper.kmp.activitypub.frontend.ui.ProfileMediaUiState
import net.matsudamper.kmp.activitypub.frontend.ui.previewFollowMenu

@OptIn(ExperimentalCoilApi::class)
@Preview
@Composable
private fun UserProfileContentPreview() {
    MaterialTheme {
        CompositionLocalProvider(
            LocalInspectionMode provides true,
            LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { ColorImage(0xFF5C6BC0.toInt()) },
        ) {
            UserProfileContent(
                uiState = UserProfileScreenUiState(
                    content = UserProfileScreenUiState.Content.Loaded(
                        acct = "@alice@example.social",
                        iconUrl = null,
                        headerUrl = "https://example.social/header.png",
                        displayName = createTextWithEmojis(text = "Alice", emojis = listOf()),
                        biography = "Kotlin と写真が好きです。\n日々のことを投稿しています。",
                        links = listOf(),
                        followMenu = previewFollowMenu(destinationName = null),
                        selectedTab = ProfileContentTab.Posts,
                        posts = listOf(
                            PostItemUiState(
                                pinnedLabelVisible = true,
                                deletedLabelVisible = false,
                                actorLabel = null,
                                iconUrl = null,
                                displayName = createTextWithEmojis(text = "Alice", emojis = listOf()),
                                acct = "@alice@example.social",
                                body = createPostBodyText(content = "今日はプロフィール画面を作っています。 https://kotlinlang.org/", emojis = listOf(), onOpenUrl = {}),
                                postedAt = "2026-11-01T12:00:00Z",
                                fullPostedAt = "2026/11/01 21:00",
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
                                boostCountText = "4",
                                favouriteButton = PostItemUiState.FavouriteButton(iconHighlighted = true, listener = AndroidPreviewFavouriteButtonListener),
                                boostButton = PostItemUiState.BoostButton(iconHighlighted = false, listener = AndroidPreviewBoostButtonListener),
                                stamps = listOf(PostItemUiState.Stamp(emoji = "❤️", imageUrl = null, countText = "4")),
                                authorListener = null,
                                menuItems = listOf(),
                                postListener = null,
                                shareListener = null,
                                listener = AndroidPreviewPostItemListener,
                            ),
                            PostItemUiState(
                                pinnedLabelVisible = false,
                                deletedLabelVisible = false,
                                actorLabel = PostItemUiState.ActorLabel(
                                    icon = PostItemUiState.ActorLabelIcon.Boost,
                                    actorName = createTextWithEmojis(text = "Alice", emojis = listOf()),
                                    actionText = "がブースト",
                                    iconUrl = null,
                                    cancelledLabelVisible = true,
                                    listener = null,
                                ),
                                iconUrl = null,
                                displayName = createTextWithEmojis(text = "Bob", emojis = listOf()),
                                acct = "@bob@remote.example",
                                body = createPostBodyText(content = "フォローしていない人の投稿もブーストできます", emojis = listOf(), onOpenUrl = {}),
                                postedAt = "2026-11-01T13:00:00Z",
                                fullPostedAt = "2026/11/01 22:00",
                                linkPreviews = listOf(),
                                images = listOf(),
                                favouriteCountText = null,
                                boostCountText = null,
                                favouriteButton = PostItemUiState.FavouriteButton(iconHighlighted = false, listener = AndroidPreviewFavouriteButtonListener),
                                boostButton = PostItemUiState.BoostButton(iconHighlighted = false, listener = AndroidPreviewBoostButtonListener),
                                stamps = listOf(),
                                authorListener = null,
                                menuItems = listOf(),
                                postListener = null,
                                shareListener = null,
                                listener = AndroidPreviewPostItemListener,
                            ),
                        ),
                        postsEmptyMessage = null,
                        media = listOf(),
                        mediaEmptyMessage = "表示できるメディアはありません",
                    ),
                    deleteNoteDialog = null,
                    addFollowingGroupDialog = null,
                    openOriginalMenuItemVisible = true,
                    listener = AndroidPreviewUserProfileListener,
                ),
                snackbarHostState = SnackbarHostState(),
            )
        }
    }
}

@Preview
@Composable
private fun UserProfileContentLocalUserPreview() {
    MaterialTheme {
        UserProfileContent(
            uiState = LocalUserUiState,
            snackbarHostState = SnackbarHostState(),
        )
    }
}

@Preview(widthDp = 1200, heightDp = 800)
@Composable
private fun UserProfileDialogPreview() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            UserProfileDialogFrame {
                UserProfileContent(
                    uiState = LocalUserUiState,
                    snackbarHostState = SnackbarHostState(),
                )
            }
        }
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
                    headerUrl = null,
                    displayName = createTextWithEmojis(text = "Alice", emojis = listOf()),
                    biography = null,
                    links = listOf(),
                    followMenu = previewFollowMenu(destinationName = null),
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
                deleteNoteDialog = null,
                addFollowingGroupDialog = null,
                openOriginalMenuItemVisible = true,
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
                deleteNoteDialog = null,
                addFollowingGroupDialog = null,
                openOriginalMenuItemVisible = false,
                listener = AndroidPreviewUserProfileListener,
            ),
            snackbarHostState = SnackbarHostState(),
        )
    }
}

private val LocalUserUiState = UserProfileScreenUiState(
    content = UserProfileScreenUiState.Content.Loaded(
        acct = "@bob@local.example",
        iconUrl = null,
        headerUrl = null,
        displayName = createTextWithEmojis(text = "Bob", emojis = listOf()),
        biography = "ローカルのユーザーです。",
        links = listOf(
            ProfileLinkUiState(name = "GitHub", url = "https://github.com/bob", listener = AndroidPreviewProfileLinkListener),
            ProfileLinkUiState(name = "Blog", url = "https://blog.example/bob/very/long/path/to/the/article", listener = AndroidPreviewProfileLinkListener),
        ),
        followMenu = null,
        selectedTab = ProfileContentTab.Posts,
        posts = listOf(),
        postsEmptyMessage = "表示できる投稿はありません",
        media = listOf(),
        mediaEmptyMessage = "表示できるメディアはありません",
    ),
    deleteNoteDialog = null,
    addFollowingGroupDialog = null,
    openOriginalMenuItemVisible = true,
    listener = AndroidPreviewUserProfileListener,
)

private object AndroidPreviewUserProfileListener : UserProfileScreenUiState.Listener {
    override fun onClickRetry() = Unit

    override fun onClickBack() = Unit

    override fun onClickTab(tab: ProfileContentTab) = Unit

    override fun onClickOpenOriginal() = Unit
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

private object AndroidPreviewProfileLinkListener : ProfileLinkUiState.Listener {
    override fun onClick() = Unit
}

private object AndroidPreviewFavouriteButtonListener : PostItemUiState.FavouriteButtonListener {
    override fun onClick() = Unit
}

private object AndroidPreviewBoostButtonListener : PostItemUiState.BoostButtonListener {
    override fun onClick() = Unit
}
