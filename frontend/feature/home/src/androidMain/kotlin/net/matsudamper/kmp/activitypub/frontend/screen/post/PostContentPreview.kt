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
                body = "Kotlin 2.4 がリリースされました",
                error = null,
                inputEnabled = true,
                postButtonEnabled = true,
                listener = AndroidPreviewPostListener,
            ),
        )
    }
}

private object AndroidPreviewPostListener : PostScreenUiState.Listener {
    override fun onBodyChanged(text: String) = Unit

    override fun onClickPost() = Unit

    override fun onClickBack() = Unit
}
