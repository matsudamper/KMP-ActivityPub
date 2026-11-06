package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision

enum class ProfileContentTab {
    Posts,
    Media,
}

/**
 * @param description 画像の説明文。無ければ null
 * @param listener 個別投稿画面で開けない投稿の画像は押せないので null
 */
data class ProfileMediaUiState(
    val url: String,
    val description: String?,
    val blurred: Boolean,
    val listener: Listener?,
) {
    @Immutable
    interface Listener {
        fun onClick()
    }
}

/**
 * 画面の幅に合わせて列の数を変える
 */
internal val ProfileGridCells: GridCells = GridCells.Adaptive(minSize = 120.dp)

/**
 * Material3 の Tab は使わない。Web で日本語のフォントが後から読み込まれると、
 * 豆腐で測った幅のまま文字が 1 文字ずつ折り返されるため
 */
@Composable
internal fun ProfileContentTabRow(
    selectedTab: ProfileContentTab,
    onClickTab: (ProfileContentTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ProfileContentTab.entries.forEach { tab ->
                ProfileContentTabItem(
                    text = when (tab) {
                        ProfileContentTab.Posts -> "投稿"
                        ProfileContentTab.Media -> "メディア"
                    },
                    selected = tab == selectedTab,
                    onClick = { onClickTab(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun ProfileContentTabItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.height(TabTextAreaHeight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TabIndicatorHeight)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
        )
    }
}

@Composable
internal fun ProfileMediaGridItem(
    media: ProfileMediaUiState,
    modifier: Modifier = Modifier,
) {
    val listener = media.listener
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (listener == null) Modifier else Modifier.clickable(onClick = listener::onClick)),
    ) {
        if (media.blurred) {
            // Modifier.blur は Android 11 以下で効かないので、小さく読み込んで引き伸ばしたぼやけでも隠す。
            // 通常の大きさの画像がキャッシュから使われると隠れないので、キャッシュを分けて大きさも固定する
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(media.url)
                    .memoryCacheKey("blurred:${media.url}")
                    .size(BlurredMediaLoadSizePx)
                    .precision(Precision.EXACT)
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
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
            )
        } else {
            AsyncImage(
                model = media.url,
                contentDescription = media.description,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private const val BlurredMediaLoadSizePx = 24
private val TabTextAreaHeight = 45.dp
private val TabIndicatorHeight = 3.dp
