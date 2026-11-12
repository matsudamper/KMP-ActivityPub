package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import coil3.compose.AsyncImage
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons

internal enum class PostItemStyle {
    /** 並べて流し読みするので、文字を小さめにし、画像は横にスクロールして投稿 1 件の高さを抑える */
    Timeline,

    /** 1 件だけを読むので、文字やボタンを大きくし、画像は折り返して全部を一度に見せる */
    Detail,
}

@Immutable
private class PostItemSizes(
    val displayNameTextStyle: TextStyle,
    val secondaryTextStyle: TextStyle,
    val bodyTextStyle: TextStyle,
    val countTextStyle: TextStyle,
    val iconSize: Dp,
    val actionIconSize: Dp,
    val actionSlotWidth: Dp,
    val menuButtonSize: Dp,
    val stampImageHeight: Dp,
)

@Composable
private fun PostItemStyle.sizes(): PostItemSizes {
    val typography = MaterialTheme.typography
    return when (this) {
        PostItemStyle.Timeline -> PostItemSizes(
            displayNameTextStyle = typography.titleSmall,
            secondaryTextStyle = typography.bodyMedium,
            bodyTextStyle = typography.bodyMedium,
            countTextStyle = typography.bodySmall,
            iconSize = 46.dp,
            actionIconSize = 24.dp,
            actionSlotWidth = 76.dp,
            menuButtonSize = 32.dp,
            stampImageHeight = 18.dp,
        )

        PostItemStyle.Detail -> PostItemSizes(
            displayNameTextStyle = typography.titleMedium,
            secondaryTextStyle = typography.bodyLarge,
            bodyTextStyle = typography.bodyLarge,
            countTextStyle = typography.bodyMedium,
            iconSize = 56.dp,
            actionIconSize = 28.dp,
            actionSlotWidth = 88.dp,
            menuButtonSize = 40.dp,
            stampImageHeight = 22.dp,
        )
    }
}

/**
 * タイムラインやプロフィールに並べる投稿 1 件
 */
@Composable
internal fun PostItem(
    uiState: PostItemUiState,
    style: PostItemStyle,
    modifier: Modifier = Modifier,
) {
    val sizes = style.sizes()
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
        if (uiState.pinnedLabelVisible) {
            PinnedLabel(sizes = sizes)
        }
        if (uiState.deletedLabelVisible) {
            DeletedLabel(sizes = sizes)
        }
        val actorLabel = uiState.actorLabel
        if (actorLabel != null) {
            ActorLabel(actorLabel = actorLabel, sizes = sizes)
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
                        .size(sizes.iconSize)
                        .clip(CircleShape),
                )
                Column(modifier = Modifier.weight(1f)) {
                    EmojiText(
                        text = uiState.displayName,
                        style = sizes.displayNameTextStyle,
                        color = Color.Unspecified,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = uiState.acct,
                        style = sizes.secondaryTextStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            when (style) {
                PostItemStyle.Timeline -> Text(
                    text = uiState.postedAt,
                    style = sizes.secondaryTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )

                PostItemStyle.Detail -> Unit
            }
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
                    style = sizes.bodyTextStyle,
                    color = Color.Unspecified,
                    fontWeight = null,
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Clip,
                )
            }
            if (style == PostItemStyle.Detail && uiState.images.isNotEmpty()) {
                PostImageGrid(
                    images = uiState.images,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val horizontalScrollImages = when (style) {
                PostItemStyle.Timeline -> uiState.images
                PostItemStyle.Detail -> listOf()
            }
            if (horizontalScrollImages.isNotEmpty() || uiState.linkPreviews.isNotEmpty()) {
                val attachmentListState = rememberLazyListState()
                val layoutDirection = LocalLayoutDirection.current
                val attachmentFlingBehavior = ScrollableDefaults.flingBehavior()
                LazyRow(
                    state = attachmentListState,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .bleedHorizontally(start = PostHorizontalPadding, end = PostHorizontalPadding)
                        .scrollHorizontallyByMouseDrag(
                            state = attachmentListState,
                            flingBehavior = attachmentFlingBehavior,
                            reverseDirection = layoutDirection == LayoutDirection.Rtl,
                        ),
                    contentPadding = PaddingValues(horizontal = PostHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(horizontalScrollImages) { image ->
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
            if (style == PostItemStyle.Detail) {
                Text(
                    text = uiState.fullPostedAt,
                    modifier = Modifier.padding(top = 8.dp),
                    style = sizes.secondaryTextStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PostActions(uiState = uiState, sizes = sizes)
            if (uiState.stamps.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    uiState.stamps.forEach { stamp ->
                        ReactionCount(countText = stamp.countText, sizes = sizes) {
                            StampEmoji(stamp = stamp, sizes = sizes)
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
private fun PostImageGrid(
    images: List<PostItemUiState.Image>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val columnCount = (maxWidth / PostImageGridMinCellWidth).toInt()
            .coerceIn(1, PostImageGridMaxColumnCount)
            .coerceAtMost(images.size)
        Column(verticalArrangement = Arrangement.spacedBy(PostImageGridSpacing)) {
            images.chunked(columnCount).forEach { rowImages ->
                Row(horizontalArrangement = Arrangement.spacedBy(PostImageGridSpacing)) {
                    rowImages.forEach { image ->
                        PostImageTile(
                            image = image,
                            modifier = Modifier.weight(1f).aspectRatio(PostImageGridCellAspectRatio),
                        )
                    }
                    repeat(columnCount - rowImages.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * ブーストの行と同じく、アイコンの列に揃えて出す
 */
@Composable
private fun PinnedLabel(sizes: PostItemSizes) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(PostIconHeaderSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(sizes.iconSize), contentAlignment = Alignment.CenterEnd) {
            Icon(
                imageVector = AppIcons.PushPin,
                contentDescription = null,
                modifier = Modifier.size(sizes.actionIconSize),
                tint = color,
            )
        }
        Text(
            text = "ピン留め",
            style = sizes.secondaryTextStyle,
            color = color,
        )
    }
}

/**
 * ピン留めの行と同じく、本文の列に揃えて出す
 */
@Composable
private fun DeletedLabel(sizes: PostItemSizes) {
    Row(horizontalArrangement = Arrangement.spacedBy(PostIconHeaderSpacing)) {
        Spacer(modifier = Modifier.width(sizes.iconSize))
        Text(
            text = "削除",
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
            style = sizes.secondaryTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * アイコンの列に揃えて出す。本文の列に揃えると、誰の投稿かを示す名前の行と見分けにくい
 */
@Composable
private fun ActorLabel(
    actorLabel: PostItemUiState.ActorLabel,
    sizes: PostItemSizes,
) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val listener = actorLabel.listener
    Row(
        modifier = Modifier
            .bleedHorizontally(start = PostHorizontalPadding, end = PostHorizontalPadding)
            .then(if (listener == null) Modifier else Modifier.clickable(onClick = listener::onClick))
            .padding(horizontal = PostHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(PostIconHeaderSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(sizes.iconSize), contentAlignment = Alignment.CenterEnd) {
            Icon(
                imageVector = when (actorLabel.icon) {
                    PostItemUiState.ActorLabelIcon.Boost -> AppIcons.Repeat
                    PostItemUiState.ActorLabelIcon.Favourite -> AppIcons.Star
                },
                contentDescription = null,
                modifier = Modifier.size(sizes.actionIconSize),
                tint = color,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileIcon(
                iconUrl = actorLabel.iconUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(ActorLabelIconSize)
                    .clip(CircleShape),
            )
            EmojiText(
                modifier = Modifier.weight(1f, fill = false),
                text = actorLabel.actorName,
                style = sizes.secondaryTextStyle,
                color = color,
                fontWeight = null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = actorLabel.actionText,
                style = sizes.secondaryTextStyle,
                color = color,
                maxLines = 1,
            )
            if (actorLabel.cancelledLabelVisible) {
                Text(
                    text = "取り消し",
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                    style = sizes.secondaryTextStyle,
                    color = color,
                    maxLines = 1,
                )
            }
        }
    }
}

private val ActorLabelIconSize = 20.dp

@Composable
private fun ReactionCount(
    countText: String,
    sizes: PostItemSizes,
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
            style = sizes.countTextStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * カスタム絵文字の画像が取れなければ :name: の文字で出す。何も出さないと数だけが残る
 */
@Composable
private fun StampEmoji(
    stamp: PostItemUiState.Stamp,
    sizes: PostItemSizes,
) {
    val imageUrl = stamp.imageUrl
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    if (imageUrl == null || imageFailed) {
        Text(
            text = stamp.emoji,
            style = sizes.secondaryTextStyle,
        )
    } else {
        AsyncImage(
            model = imageUrl,
            contentDescription = stamp.emoji,
            modifier = Modifier.height(sizes.stampImageHeight).widthIn(max = sizes.stampImageHeight * 3),
            contentScale = ContentScale.Fit,
            onError = { imageFailed = true },
        )
    }
}

/**
 * 数が届く前と後で投稿の高さが変わらないよう、お気に入りが 0 件でも行ごと出しておく。
 * 数の桁で隣が動かないよう、操作ごとに同じ幅を取る。スタンプはまだ押せないので、押せないアイコンだけを置く
 */
@Composable
private fun PostActions(
    uiState: PostItemUiState,
    sizes: PostItemSizes,
) {
    val iconColor = MaterialTheme.colorScheme.onSurfaceVariant
    val disabledIconColor = iconColor.copy(alpha = 0.38f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 狭い画面で操作が並びきらなくても、メニュー（削除・ピン留め）を押せるよう、残りの幅だけを操作に使う
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val boostButton = uiState.boostButton
            Row(
                modifier = Modifier
                    .width(sizes.actionSlotWidth)
                    .then(
                        if (boostButton == null) {
                            Modifier
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { boostButton.listener.onClick() }
                                .semantics { stateDescription = if (boostButton.iconHighlighted) "ブースト済み" else "未ブースト" }
                        },
                    ),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = AppIcons.Repeat,
                    contentDescription = "ブースト",
                    modifier = Modifier.size(sizes.actionIconSize),
                    tint = when {
                        boostButton == null -> disabledIconColor
                        boostButton.iconHighlighted -> BoostHighlightedColor
                        else -> iconColor
                    },
                )
                Text(
                    text = uiState.boostCountText.orEmpty(),
                    style = sizes.countTextStyle,
                    color = iconColor,
                    maxLines = 1,
                )
            }
            val favouriteButton = uiState.favouriteButton
            Row(
                modifier = Modifier
                    .width(sizes.actionSlotWidth)
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
                    modifier = Modifier.size(sizes.actionIconSize),
                    tint = if (favouriteButton?.iconHighlighted == true) FavouriteHighlightedColor else iconColor,
                )
                Text(
                    text = uiState.favouriteCountText.orEmpty(),
                    style = sizes.countTextStyle,
                    color = iconColor,
                    maxLines = 1,
                )
            }
            Box(modifier = Modifier.width(sizes.actionSlotWidth)) {
                Icon(
                    imageVector = AppIcons.Emoticon,
                    contentDescription = "スタンプ",
                    modifier = Modifier.size(sizes.actionIconSize),
                    tint = disabledIconColor,
                )
            }
            val shareListener = uiState.shareListener
            Icon(
                imageVector = AppIcons.Share,
                contentDescription = "共有",
                modifier = Modifier
                    .size(sizes.actionIconSize)
                    .clip(CircleShape)
                    .clickable(enabled = shareListener != null) { shareListener?.onClick() },
                tint = if (shareListener == null) disabledIconColor else iconColor,
            )
        }
        if (uiState.menuItems.isNotEmpty()) {
            PostMenu(menuItems = uiState.menuItems, sizes = sizes)
        }
    }
}

private val FavouriteHighlightedColor = Color(0xFFF5B400)

private val BoostHighlightedColor = Color(0xFF2E9E5B)

@Composable
private fun PostMenu(
    menuItems: List<PostItemUiState.MenuItem>,
    sizes: PostItemSizes,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.size(sizes.menuButtonSize),
        ) {
            Icon(
                imageVector = AppIcons.MoreVert,
                contentDescription = "メニュー",
                modifier = Modifier.size(sizes.actionIconSize),
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

private val PostHorizontalPadding = 16.dp
private val PostBodySelectionVerticalMargin = 4.dp
private val PostImageGridMinCellWidth = 180.dp
private val PostImageGridSpacing = 8.dp
private const val PostImageGridMaxColumnCount = 3
private const val PostImageGridCellAspectRatio = 4f / 3f
private val PostIconHeaderSpacing = 12.dp

/**
 * スクロールの部品はマウスのドラッグでは動かないので、画面に収まらない画像をマウスで送れるようにする。
 * ドラッグで動かした押下は消費して、離したときに画像を開かないようにする
 */
private fun Modifier.scrollHorizontallyByMouseDrag(
    state: LazyListState,
    flingBehavior: FlingBehavior,
    reverseDirection: Boolean,
): Modifier = pointerInput(state, flingBehavior, reverseDirection) {
    val toScrollAmount = { dragAmount: Float -> if (reverseDirection) dragAmount else -dragAmount }
    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (down.type == PointerType.Mouse) {
                launch { state.stopScroll() }
                val velocityTracker = VelocityTracker()
                velocityTracker.addPointerInputChange(down)
                val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                    velocityTracker.addPointerInputChange(change)
                    change.consume()
                }
                if (dragStart != null) {
                    val dragCompleted = horizontalDrag(dragStart.id) { change ->
                        velocityTracker.addPointerInputChange(change)
                        state.dispatchRawDelta(toScrollAmount(change.positionChange().x))
                        change.consume()
                    }
                    if (dragCompleted) {
                        // 止めてから離したときに慣性を付けないよう、離した時刻も速さの計算に入れる
                        val release = currentEvent.changes.firstOrNull { it.id == dragStart.id }
                        if (release != null) {
                            velocityTracker.addPointerInputChange(release)
                        }
                        val flingVelocity = toScrollAmount(velocityTracker.calculateVelocity().x)
                        launch {
                            state.scroll {
                                with(flingBehavior) { performFling(flingVelocity) }
                            }
                        }
                    }
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
