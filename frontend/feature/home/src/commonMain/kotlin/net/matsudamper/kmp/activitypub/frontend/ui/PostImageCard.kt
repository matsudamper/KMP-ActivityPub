package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * 投稿に付いた画像 1 枚。OGP のカードと並べるので、同じ幅で高さは並びに合わせる
 */
@Composable
internal fun PostImageCard(
    image: PostItemUiState.Image,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(LinkPreviewCardWidth)
            .heightIn(min = LinkPreviewImageHeight)
            .fillMaxHeight(),
        onClick = image.listener::onClick,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box {
            // matchParentSize にして画像の縦横比を並びの高さの計算に混ぜない。縦長の画像で行が伸びるのを防ぐ
            AsyncImage(
                model = image.url,
                contentDescription = image.description,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
