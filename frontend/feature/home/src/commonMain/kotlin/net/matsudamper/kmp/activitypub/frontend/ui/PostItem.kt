package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val postListener = uiState.postListener
    val postInteractionSource = remember { HoverIgnoringInteractionSource() }
    val postClickModifier =
        if (postListener == null) {
            Modifier
        } else {
            Modifier.clickable(
                interactionSource = postInteractionSource,
                indication = LocalIndication.current,
                onClick = postListener::onClick,
            )
        }
    Row(
        modifier = modifier.fillMaxWidth().then(postClickModifier).padding(horizontal = 16.dp, vertical = 12.dp),
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
                            faviconUrl = preview.faviconUrl,
                            onClick = preview.listener::onClick,
                        )
                    }
                }
            }
            PostActions(uiState = uiState)
            if (uiState.stamps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    uiState.stamps.forEach { stamp ->
                        ReactionCount(countText = stamp.countText) {
                            StampEmoji(stamp = stamp)
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

/**
 * カスタム絵文字の画像が取れなければ :name: の文字で出す。何も出さないと数だけが残る
 */
@Composable
private fun StampEmoji(stamp: PostItemUiState.Stamp) {
    val imageUrl = stamp.imageUrl
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    if (imageUrl == null || imageFailed) {
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
            onError = { imageFailed = true },
        )
    }
}

/**
 * 数が届く前と後で投稿の高さが変わらないよう、お気に入りが 0 件でも行ごと出しておく。
 * 数の桁で隣が動かないよう、操作ごとに同じ幅を取る。ブースト・スタンプ・共有はまだ押せないので、押せないアイコンだけを置く
 */
@Composable
private fun PostActions(uiState: PostItemUiState) {
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val disabledIconColor = iconColor.copy(alpha = 0.38f)
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(PostActionSlotWidth)) {
            Icon(
                imageVector = AppIcons.Repeat,
                contentDescription = "ブースト",
                modifier = Modifier.size(PostActionIconSize),
                tint = disabledIconColor,
            )
        }
        Row(
            modifier = Modifier.width(PostActionSlotWidth),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppIcons.Star,
                contentDescription = "お気に入り",
                modifier = Modifier.size(PostActionIconSize),
                tint = iconColor,
            )
            Text(
                text = uiState.favouriteCountText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = iconColor,
                maxLines = 1,
            )
        }
        Box(modifier = Modifier.width(PostActionSlotWidth)) {
            Icon(
                imageVector = AppIcons.Emoticon,
                contentDescription = "スタンプ",
                modifier = Modifier.size(PostActionIconSize),
                tint = disabledIconColor,
            )
        }
        Icon(
            imageVector = AppIcons.Share,
            contentDescription = "共有",
            modifier = Modifier.size(PostActionIconSize),
            tint = disabledIconColor,
        )
    }
}

private val PostActionIconSize = 18.dp

private val PostActionSlotWidth = 64.dp
