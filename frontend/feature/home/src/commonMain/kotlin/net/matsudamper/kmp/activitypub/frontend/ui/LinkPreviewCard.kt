package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * リンク先の OGP 1 つ分。横に並べる前提で幅は固定
 *
 * @param imageUrl null でも画像の枠は取っておき、取れたときにカードの大きさを変えない
 * @param faviconUrl imageUrl が null のとき、画像の枠の中央に小さく出す
 */
@Composable
internal fun LinkPreviewCard(
    title: String,
    siteName: String,
    imageUrl: String?,
    faviconUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(LinkPreviewCardWidth),
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        LinkPreviewCardContent(
            title = title,
            siteName = siteName,
            imageUrl = imageUrl,
            faviconUrl = faviconUrl,
        )
    }
}

/**
 * 画像のカードも OGP の有無で高さが変わらないよう、透明にしたこの中身で大きさを決める
 */
@Composable
internal fun LinkPreviewCardContent(
    title: String,
    siteName: String,
    imageUrl: String?,
    faviconUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(OgpImageAspectRatio)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else if (faviconUrl != null) {
                AsyncImage(
                    model = faviconUrl,
                    contentDescription = null,
                    modifier = Modifier.size(FaviconSize),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = siteName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal val LinkPreviewCardWidth: Dp = 240.dp

private val FaviconSize: Dp = 32.dp

/**
 * og:image の推奨サイズ 1200x630 の比率
 */
private const val OgpImageAspectRatio: Float = 1200f / 630f
