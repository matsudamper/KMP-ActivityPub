package net.matsudamper.kmp.activitypub.android.server

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.screen.PreviewsMultiSize

@PreviewsMultiSize
@Composable
private fun ServerContentPreview() {
    MaterialTheme {
        ServerContent(
            uiState = ServerScreenUiState(
                serverUrl = "example.com",
                error = null,
                connectButtonEnabled = true,
                listener = object : ServerScreenUiState.Listener {
                    override fun onServerUrlChanged(text: String) = Unit

                    override fun onClickConnect() = Unit
                },
            ),
        )
    }
}
