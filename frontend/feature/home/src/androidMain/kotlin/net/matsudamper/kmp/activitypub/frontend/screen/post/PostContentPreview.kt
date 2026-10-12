package net.matsudamper.kmp.activitypub.frontend.screen.post

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun PostContentPreview() {
    MaterialTheme {
        PostContent(
            uiState = PostScreenUiState(
                message = "投稿",
            ),
        )
    }
}
