package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

/**
 * タイムラインやプロフィールに並べる投稿 1 件
 */
@Composable
internal fun PostItem(
    uiState: PostItemUiState,
    modifier: Modifier = Modifier,
) {
    val authorListener = uiState.authorListener
    val authorClickModifier = if (authorListener == null) Modifier else Modifier.clickable(onClick = authorListener::onClick)
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileIcon(
            iconUrl = uiState.iconUrl,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(authorClickModifier),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = authorClickModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = uiState.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = uiState.acct,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = uiState.postedAt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Text(
                text = uiState.body,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (uiState.images.isNotEmpty() || uiState.linkPreviews.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.images) { image ->
                        PostImageCard(image = image)
                    }
                    items(uiState.linkPreviews) { preview ->
                        LinkPreviewCard(
                            title = preview.title,
                            siteName = preview.siteName,
                            imageUrl = preview.imageUrl,
                            onClick = preview.listener::onClick,
                        )
                    }
                }
            }
            val favouriteCountText = uiState.favouriteCountText
            if (favouriteCountText != null || uiState.stamps.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (favouriteCountText != null) {
                        ReactionCount(countText = favouriteCountText) {
                            Icon(
                                imageVector = AppIcons.Star,
                                contentDescription = "お気に入り",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    uiState.stamps.forEach { stamp ->
                        ReactionCount(countText = stamp.countText) {
                            val imageUrl = stamp.imageUrl
                            if (imageUrl == null) {
                                Text(
                                    text = stamp.emoji,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            } else {
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = stamp.emoji,
                                    modifier = Modifier.height(18.dp).widthIn(max = 54.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(uiState.listener) {
        uiState.listener.onVisible()
    }
}

@Composable
private fun ReactionCount(
    countText: String,
    reaction: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        reaction()
        Text(
            text = countText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
