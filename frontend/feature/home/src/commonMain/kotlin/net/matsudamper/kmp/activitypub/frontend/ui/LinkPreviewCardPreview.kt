package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler

@OptIn(ExperimentalCoilApi::class)
@Preview
@Composable
private fun LinkPreviewCardPreview() {
    CompositionLocalProvider(
        LocalInspectionMode provides true,
        LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { ColorImage(0xFF26A69A.toInt()) },
    ) {
        MaterialTheme {
            Surface {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinkPreviewCard(
                        title = "Kotlin Programming Language",
                        siteName = "Kotlin",
                        imageUrl = "https://example.com/ogp.png",
                        faviconUrl = "https://example.com/favicon.ico",
                        onClick = {},
                    )
                    LinkPreviewCard(
                        title = "OGP 画像の無いページ",
                        siteName = "example.com",
                        imageUrl = null,
                        faviconUrl = "https://example.com/favicon.ico",
                        onClick = {},
                    )
                }
            }
        }
    }
}
