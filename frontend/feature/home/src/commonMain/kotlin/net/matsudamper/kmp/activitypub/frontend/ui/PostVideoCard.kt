package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * 投稿に付いた動画 1 本。画像のカードと並べるので同じ大きさにし、押すと全画面で再生する
 */
@Composable
internal fun PostVideoCard(
    video: PostItemUiState.Video,
    modifier: Modifier = Modifier,
) {
    // LazyRow は位置で状態を持つので、開いている間に動画の並びが変わっても選んだ動画を出し続けるよう URL で持つ
    var playingVideoUrl by remember { mutableStateOf<String?>(null) }
    Surface(
        modifier = modifier.width(LinkPreviewCardWidth),
        onClick = { playingVideoUrl = video.url },
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box {
            // 画像のカードと同じく、OGP のカードと同じ中身で高さを測る
            LinkPreviewCardContent(
                title = "",
                siteName = "",
                imageUrl = null,
                faviconUrl = null,
                modifier = Modifier.alpha(0f),
            )
            if (video.firstFrameVisible) {
                VideoFirstFrame(videoUrl = video.url, modifier = Modifier.matchParentSize())
            }
            Surface(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Icon(
                    imageVector = AppIcons.PlayArrow,
                    contentDescription = "再生する",
                    modifier = Modifier.padding(8.dp),
                )
            }
            val description = video.description
            if (description != null) {
                Text(
                    text = description,
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    val currentPlayingVideoUrl = playingVideoUrl
    if (currentPlayingVideoUrl != null) {
        VideoPlayerDialog(
            videoUrl = currentPlayingVideoUrl,
            onDismissRequest = { playingVideoUrl = null },
        )
    }
}
