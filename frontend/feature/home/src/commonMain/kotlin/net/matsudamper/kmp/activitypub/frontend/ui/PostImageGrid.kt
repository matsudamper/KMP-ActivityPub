package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * 投稿に付いた画像を並べる。1 枚なら幅いっぱい、2 枚以上は 2 列。読み込みの前後で投稿の高さが変わらないよう、枠の比率は固定
 */
@Composable
internal fun PostImageGrid(
    images: List<PostItemUiState.Image>,
    modifier: Modifier = Modifier,
) {
    val columnCount = if (images.size == 1) 1 else MultipleImageColumnCount
    val aspectRatio = if (images.size == 1) SingleImageAspectRatio else MultipleImageAspectRatio
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        images.chunked(columnCount).forEach { rowImages ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowImages.forEach { image ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(aspectRatio)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = image.listener::onClick),
                    ) {
                        AsyncImage(
                            model = image.url,
                            contentDescription = image.description,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }
                // 奇数枚の最後の行も、ほかの行と同じ幅で出す
                repeat(columnCount - rowImages.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private const val MultipleImageColumnCount: Int = 2
private const val SingleImageAspectRatio: Float = 16f / 9f
private const val MultipleImageAspectRatio: Float = 4f / 3f
