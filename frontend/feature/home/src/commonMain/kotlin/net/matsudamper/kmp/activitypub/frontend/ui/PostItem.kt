package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
    Column(
        modifier = modifier.fillMaxWidth().then(postClickModifier).padding(horizontal = PostHorizontalPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val boostedBy = uiState.boostedBy
        if (boostedBy != null) {
            BoostedByLabel(boostedBy = boostedBy)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier.weight(1f).then(authorClickModifier),
                horizontalArrangement = Arrangement.spacedBy(PostIconHeaderSpacing),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProfileIcon(
                    iconUrl = uiState.iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(PostIconSize)
                        .clip(CircleShape),
                )
                Column(modifier = Modifier.weight(1f)) {
                    EmojiText(
                        text = uiState.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.Unspecified,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = uiState.acct,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = uiState.postedAt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PostBodySelectionContainer(
                modifier = Modifier.bleed(horizontal = PostHorizontalPadding, vertical = PostBodySelectionVerticalMargin),
                contentPadding = PaddingValues(horizontal = PostHorizontalPadding, vertical = PostBodySelectionVerticalMargin),
            ) {
                EmojiText(
                    text = uiState.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Unspecified,
                    fontWeight = null,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            }
            if (uiState.images.isNotEmpty() || uiState.linkPreviews.isNotEmpty()) {
                val attachmentListState = rememberLazyListState()
                val layoutDirection = LocalLayoutDirection.current
                LazyRow(
                    state = attachmentListState,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .bleedHorizontally(start = PostHorizontalPadding, end = PostHorizontalPadding)
                        .scrollHorizontallyByMouseDrag(
                            state = attachmentListState,
                            reverseDirection = layoutDirection == LayoutDirection.Rtl,
                        ),
                    contentPadding = PaddingValues(horizontal = PostHorizontalPadding),
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

/**
 * アイコンの列に揃えて出す。本文の列に揃えると、誰の投稿かを示す名前の行と見分けにくい
 */
@Composable
private fun BoostedByLabel(boostedBy: PostItemUiState.BoostedBy) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val listener = boostedBy.listener
    Row(
        modifier = Modifier
            .bleedHorizontally(start = PostHorizontalPadding, end = PostHorizontalPadding)
            .then(if (listener == null) Modifier else Modifier.clickable(onClick = listener::onClick))
            .padding(horizontal = PostHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(PostIconHeaderSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(PostIconSize), contentAlignment = Alignment.CenterEnd) {
            Icon(
                imageVector = AppIcons.Repeat,
                contentDescription = null,
                modifier = Modifier.size(PostActionIconSize),
                tint = color,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileIcon(
                iconUrl = boostedBy.iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(BoostedByIconSize)
                    .clip(CircleShape),
            )
            EmojiText(
                text = boostedBy.text,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                fontWeight = null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val BoostedByIconSize = 20.dp

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
 * 数の桁で隣が動かないよう、操作ごとに同じ幅を取る。ブースト・スタンプはまだ押せないので、押せないアイコンだけを置く
 */
@Composable
private fun PostActions(uiState: PostItemUiState) {
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val disabledIconColor = iconColor.copy(alpha = 0.38f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.width(PostActionSlotWidth),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppIcons.Repeat,
                contentDescription = "ブースト",
                modifier = Modifier.size(PostActionIconSize),
                tint = disabledIconColor,
            )
            Text(
                text = uiState.boostCountText.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = iconColor,
                maxLines = 1,
            )
        }
        val favouriteButton = uiState.favouriteButton
        Row(
            modifier = Modifier
                .width(PostActionSlotWidth)
                .then(
                    if (favouriteButton == null) {
                        Modifier
                    } else {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { favouriteButton.listener.onClick() }
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = AppIcons.Star,
                contentDescription = "お気に入り",
                modifier = Modifier.size(PostActionIconSize),
                tint = if (favouriteButton?.iconHighlighted == true) FavouriteHighlightedColor else iconColor,
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
        val shareListener = uiState.shareListener
        Icon(
            imageVector = AppIcons.Share,
            contentDescription = "共有",
            modifier = Modifier
                .size(PostActionIconSize)
                .clip(CircleShape)
                .clickable(enabled = shareListener != null) { shareListener?.onClick() },
            tint = if (shareListener == null) disabledIconColor else iconColor,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (uiState.menuItems.isNotEmpty()) {
            PostMenu(menuItems = uiState.menuItems)
        }
    }
}

private val PostActionIconSize = 24.dp

private val FavouriteHighlightedColor = Color(0xFFF5B400)

private val PostActionSlotWidth = 76.dp

@Composable
private fun PostMenu(menuItems: List<PostItemUiState.MenuItem>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(PostMenuButtonSize),
        ) {
            Icon(
                imageVector = AppIcons.MoreVert,
                contentDescription = "メニュー",
                modifier = Modifier.size(PostActionIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            menuItems.forEach { item ->
                DropdownMenuItem(
                    text = { Text(item.label) },
                    onClick = {
                        expanded = false
                        item.listener.onClick()
                    },
                )
            }
        }
    }
}

private val PostMenuButtonSize = 32.dp

private val PostHorizontalPadding = 16.dp
private val PostBodySelectionVerticalMargin = 4.dp
private val PostIconSize = 46.dp
private val PostIconHeaderSpacing = 12.dp

/**
 * スクロールの部品はマウスのドラッグでは動かないので、画面に収まらない画像をマウスで送れるようにする。
 * ドラッグで動かした押下は消費して、離したときに画像を開かないようにする
 */
private fun Modifier.scrollHorizontallyByMouseDrag(
    state: LazyListState,
    reverseDirection: Boolean,
): Modifier = pointerInput(state, reverseDirection) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.type == PointerType.Mouse) {
            val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                change.consume()
            }
            if (dragStart != null) {
                horizontalDrag(dragStart.id) { change ->
                    val dragAmount = change.positionChange().x
                    state.dispatchRawDelta(if (reverseDirection) dragAmount else -dragAmount)
                    change.consume()
                }
            }
        }
    }
}

// 投稿の左右の余白まで横スクロールの表示領域を広げ、スクロール中の画像が余白の手前で切れないようにする
private fun Modifier.bleedHorizontally(start: Dp, end: Dp): Modifier = layout { measurable, constraints ->
    val startPx = start.roundToPx()
    val endPx = end.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.maxWidth + startPx + endPx,
            maxWidth = constraints.maxWidth + startPx + endPx,
        ),
    )
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative(-startPx, 0)
    }
}

// 本文の位置は変えずに、押せる範囲だけを周りの余白へ広げる
private fun Modifier.bleed(horizontal: Dp, vertical: Dp): Modifier = layout { measurable, constraints ->
    val horizontalPx = horizontal.roundToPx()
    val verticalPx = vertical.roundToPx()
    val width = constraints.maxWidth + horizontalPx * 2
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = width,
            maxWidth = width,
            minHeight = 0,
            maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + verticalPx * 2 else constraints.maxHeight,
        ),
    )
    layout(constraints.maxWidth, (placeable.height - verticalPx * 2).coerceAtLeast(0)) {
        placeable.placeRelative(-horizontalPx, -verticalPx)
    }
}
