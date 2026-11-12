package net.matsudamper.kmp.activitypub.frontend.screen.image

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview
@Composable
private fun DownloadConfirmDialogPreview() {
    MaterialTheme {
        DownloadConfirmDialog(
            onClickDownload = {},
            onDismiss = {},
        )
    }
}
