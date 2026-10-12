package net.matsudamper.kmp.activitypub.frontend.screen.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun HomeContentPreview() {
    MaterialTheme {
        HomeContent(
            uiState = HomeScreenUiState(
                timelineItems = listOf(
                    HomeScreenUiState.TimelineItem(
                        displayName = "Kotlin",
                        acct = "@kotlin@example.com",
                        body = "Kotlin 2.4 がリリースされました",
                        postedAt = "1分前",
                    ),
                    HomeScreenUiState.TimelineItem(
                        displayName = "Compose",
                        acct = "@compose@example.com",
                        body = "Compose Multiplatform で Web と Android の画面を共有している",
                        postedAt = "5分前",
                    ),
                ),
                listener = AndroidPreviewHomeListener,
            ),
        )
    }
}

private object AndroidPreviewHomeListener : HomeScreenUiState.Listener {
    override fun onClickPost() = Unit

    override fun onClickLogout() = Unit
}
