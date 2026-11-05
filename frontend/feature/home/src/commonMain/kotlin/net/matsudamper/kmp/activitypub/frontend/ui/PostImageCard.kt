package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * 投稿に付いた画像 1 枚。OGP のカードと並べるので、OGP の有無にかかわらず同じ大きさにする
 */
@Composable
internal fun PostImageCard(
    image: PostItemUiState.Image,
    modifier: Modifier = Modifier,
) {
    var revealed by rememberSaveable(image.url) { mutableStateOf(false) }
    val blurred = image.blurredUntilRevealed && !revealed
    Surface(
        modifier = modifier.width(LinkPreviewCardWidth),
        onClick = {
            if (blurred) {
                revealed = true
            } else {
                image.listener.onClick()
            }
        },
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
            if (blurred) {
                // Modifier.blur は Android 11 以下で効かないので、小さく読み込んで引き伸ばしたぼやけでも隠す
                AsyncImage(
                    model = ImageRequest.Builder(LocalPlatformContext.current)
                        .data(image.url)
                        .size(BlurredImageLoadSizePx)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize().blur(32.dp),
                    contentScale = ContentScale.Crop,
                )
                Text(
                    text = "閲覧注意",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(MaterialTheme.colorScheme.inverseSurface, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                )
            } else {
                AsyncImage(
                    model = image.url,
                    contentDescription = image.description,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            if (image.blurredUntilRevealed) {
                IconButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface,
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = if (blurred) AppIcons.Visibility else AppIcons.VisibilityOff,
                        contentDescription = if (blurred) "表示する" else "隠す",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private const val BlurredImageLoadSizePx = 24
