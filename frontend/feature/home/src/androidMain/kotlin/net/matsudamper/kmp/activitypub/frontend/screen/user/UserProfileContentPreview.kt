package net.matsudamper.kmp.activitypub.frontend.screen.user

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

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
                    followingText = "フォロー中",
                    posts = listOf(UserProfileScreenUiState.Post("今日はプロフィール画面を作っています。", "2026-11-01T12:00:00Z")),
                ),
                listener = AndroidPreviewUserProfileListener,
            ),
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
        )
    }
}

private object AndroidPreviewUserProfileListener : UserProfileScreenUiState.Listener {
    override fun onClickRetry() = Unit

    override fun onClickBack() = Unit
}
