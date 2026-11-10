package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.request.ErrorResult
import coil3.request.ImageRequest

@OptIn(ExperimentalCoilApi::class)
@Preview
@Composable
private fun EmojiTextPreview() {
    CompositionLocalProvider(LocalInspectionMode provides true) {
        MaterialTheme {
            Surface {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CompositionLocalProvider(
                        LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { ColorImage(0xFF26A69A.toInt()) },
                    ) {
                        PreviewEmojiText(label = "画像を読めた")
                    }
                    CompositionLocalProvider(
                        LocalAsyncImagePreviewHandler provides FailingImagePreviewHandler,
                    ) {
                        PreviewEmojiText(label = "画像を読めなかった")
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewEmojiText(label: String) {
    EmojiText(
        text = TextWithEmojis(
            text = buildAnnotatedString {
                append("$label ")
                appendInlineContent(id = ":blob:", alternateText = ":blob:")
                append(" の絵文字")
            },
            emojis = listOf(InlineEmoji(name = ":blob:", imageUrl = "https://example.com/blob.png")),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = Color.Unspecified,
        fontWeight = null,
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Clip,
    )
}

@OptIn(ExperimentalCoilApi::class)
private object FailingImagePreviewHandler : AsyncImagePreviewHandler {
    override suspend fun handle(imageLoader: ImageLoader, request: ImageRequest): AsyncImagePainter.State =
        AsyncImagePainter.State.Error(painter = null, result = ErrorResult(image = null, request = request, throwable = IllegalStateException("プレビューでは読めない")))
}
