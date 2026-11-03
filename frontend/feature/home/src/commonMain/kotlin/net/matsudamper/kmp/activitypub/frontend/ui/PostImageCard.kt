package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * 投稿に付いた画像 1 枚。OGP のカードと並べるので、OGP の有無にかかわらず同じ大きさにする
 */
@Composable
internal fun PostImageCard(
    image: PostItemUiState.Image,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.width(LinkPreviewCardWidth),
        onClick = image.listener::onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box {
            // 文字の大きさの設定で OGP のカードの高さが変わっても揃うよう、数値で持たずに同じ中身で測る
            LinkPreviewCardContent(
                title = "",
                siteName = "",
                imageUrl = null,
                modifier = Modifier.alpha(0f),
            )
            AsyncImage(
                model = image.url,
                contentDescription = image.description,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
