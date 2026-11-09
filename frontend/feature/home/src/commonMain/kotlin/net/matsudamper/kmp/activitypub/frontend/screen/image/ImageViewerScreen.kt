package net.matsudamper.kmp.activitypub.frontend.screen.image

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.ui.ComposeCanDrawOverZoomableImage
import net.matsudamper.kmp.activitypub.frontend.ui.EscapeKeyHandler
import net.matsudamper.kmp.activitypub.frontend.ui.HorizontalArrowKeyHandler
import net.matsudamper.kmp.activitypub.frontend.ui.ZoomableImage
import net.matsudamper.kmp.activitypub.frontend.ui.icon.AppIcons
import net.matsudamper.kmp.activitypub.frontend.ui.rememberImageDownloader

@Composable
internal fun ImageViewerScreen(
    imageUrls: List<String>,
    initialIndex: Int,
    navigator: Navigator,
) {
    val downloadImage = rememberImageDownloader()
    val uiState = remember(imageUrls, initialIndex, navigator, downloadImage) {
        ImageViewerScreenUiState(
            images = imageUrls.map { imageUrl ->
                ImageViewerScreenUiState.Image(
                    url = imageUrl,
                    listener = object : ImageViewerScreenUiState.ImageListener {
                        override fun onClickConfirmDownload() {
                            downloadImage(imageUrl)
                        }
                    },
                )
            },
            initialIndex = initialIndex,
            listener = object : ImageViewerScreenUiState.Listener {
                override fun onClickClose() {
                    navigator.back()
                }

                override fun onClickOutsideImage() {
                    navigator.back()
                }
            },
        )
    }
    ImageViewerContent(uiState = uiState)
}

@Composable
internal fun ImageViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    if (ComposeCanDrawOverZoomableImage) {
        OverlayImageViewerContent(uiState = uiState)
    } else {
        ImageOnlyViewerContent(uiState = uiState)
    }
}

@Composable
internal fun DownloadConfirmDialog(
    onClickDownload: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text("画像をダウンロードしますか？") },
        confirmButton = {
            TextButton(onClick = onClickDownload) {
                Text("ダウンロード")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
    )
}

private const val INERTIA_PROJECTION_SECONDS = 0.25f

/** これ以上止まってから離した場合は、それまでの速度を慣性として使わない */
private val StoppedBeforeReleaseDuration = 100.milliseconds

/**
 * 拡大したら閉じるボタンを隠し、画像のタップで出し入れする。画像の長押しで保存するか尋ねる。
 * 左右のスワイプと矢印キーで画像を送り、拡大していないときの上下スワイプで閉じる
 */
@Composable
private fun OverlayImageViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    val pagerState = rememberPagerState(initialPage = uiState.initialIndex) { uiState.images.size }
    val coroutineScope = rememberCoroutineScope()
    var closeButtonVisible by remember { mutableStateOf(true) }
    var zoomedPages by remember { mutableStateOf(setOf<Int>()) }
    LaunchedEffect(pagerState.currentPage) {
        closeButtonVisible = pagerState.currentPage !in zoomedPages
    }
    var downloadDialogImage by remember { mutableStateOf<ImageViewerScreenUiState.Image?>(null) }
    val dismissThresholdPx = with(LocalDensity.current) { 120.dp.toPx() }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val backgroundAlpha = 0.9f * (1f - (abs(dragOffsetY) / (dismissThresholdPx * 3)).coerceIn(0f, 1f))
    val currentPageZoomed = pagerState.currentPage in zoomedPages
    var releaseAnimationJob by remember { mutableStateOf<Job?>(null) }
    val animateDragOffsetTo = { targetValue: Float, velocity: Float, onFinished: () -> Unit ->
        releaseAnimationJob = coroutineScope.launch {
            animate(
                initialValue = dragOffsetY,
                targetValue = targetValue,
                initialVelocity = velocity,
                animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> dragOffsetY = value }
            onFinished()
        }
    }
    HorizontalArrowKeyHandler(
        onPressLeft = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
        onPressRight = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .pointerInput(uiState, currentPageZoomed) {
                if (currentPageZoomed) return@pointerInput
                val velocityTracker = VelocityTracker()
                var lastDragMark: TimeMark = TimeSource.Monotonic.markNow()
                val offScreenOffset = size.height.toFloat()
                detectVerticalDragGestures(
                    onDragStart = {
                        releaseAnimationJob?.cancel()
                        velocityTracker.resetTracking()
                        lastDragMark = TimeSource.Monotonic.markNow()
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount
                        velocityTracker.addPosition(change.uptimeMillis, Offset(0f, dragOffsetY))
                        lastDragMark = TimeSource.Monotonic.markNow()
                    },
                    onDragEnd = {
                        val stoppedBeforeRelease = lastDragMark.elapsedNow() > StoppedBeforeReleaseDuration
                        val velocity = if (stoppedBeforeRelease) 0f else velocityTracker.calculateVelocity().y
                        val projectedOffset = dragOffsetY + velocity * INERTIA_PROJECTION_SECONDS
                        if (abs(projectedOffset) > dismissThresholdPx) {
                            animateDragOffsetTo(offScreenOffset * sign(projectedOffset), velocity) {
                                uiState.listener.onClickClose()
                            }
                        } else {
                            animateDragOffsetTo(0f, velocity) {}
                        }
                    },
                    onDragCancel = { animateDragOffsetTo(0f, 0f) {} },
                )
            },
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page -> page },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = dragOffsetY },
        ) { page ->
            val image = uiState.images[page]
            DisposableEffect(page) {
                onDispose { zoomedPages = zoomedPages - page }
            }
            ZoomableImage(
                imageUrl = image.url,
                onClickImage = { closeButtonVisible = !closeButtonVisible },
                onLongPressImage = { downloadDialogImage = image },
                onClickOutsideImage = uiState.listener::onClickOutsideImage,
                onZoomedChange = { zoomed ->
                    zoomedPages = if (zoomed) zoomedPages + page else zoomedPages - page
                    // 隣のページも先に組み立てられるので、見ているページの拡大だけで出し入れする
                    if (page == pagerState.currentPage) {
                        closeButtonVisible = !zoomed
                    }
                },
                onSwipeToPrevious = {},
                onSwipeToNext = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (closeButtonVisible) {
            if (uiState.images.size > 1) {
                PageNumber(
                    text = "${pagerState.currentPage + 1} / ${uiState.images.size}",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .safeDrawingPadding()
                        .padding(16.dp),
                )
            }
            CloseButton(
                uiState = uiState,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(8.dp),
            )
        }
    }
    val dialogImage = downloadDialogImage
    if (dialogImage != null) {
        DownloadConfirmDialog(
            onClickDownload = {
                downloadDialogImage = null
                dialogImage.listener.onClickConfirmDownload()
            },
            onDismiss = { downloadDialogImage = null },
        )
    }
}

/**
 * 閉じるボタンを画像に重ねられないので出さず、画像の外を押して閉じる。
 * 画像を Compose の部品で左右に送れないので、画像自身のスワイプと矢印キーで差し替える
 */
@Composable
private fun ImageOnlyViewerContent(
    uiState: ImageViewerScreenUiState,
) {
    var currentIndex by rememberSaveable(uiState) { mutableIntStateOf(uiState.initialIndex) }
    val showPrevious = { currentIndex = (currentIndex - 1).coerceAtLeast(0) }
    val showNext = { currentIndex = (currentIndex + 1).coerceAtMost(uiState.images.lastIndex) }
    HorizontalArrowKeyHandler(
        onPressLeft = showPrevious,
        onPressRight = showNext,
    )
    EscapeKeyHandler(onPressEscape = uiState.listener::onClickClose)
    ZoomableImage(
        imageUrl = uiState.images[currentIndex].url,
        onClickImage = {},
        onLongPressImage = {},
        onClickOutsideImage = uiState.listener::onClickOutsideImage,
        onZoomedChange = {},
        onSwipeToPrevious = showPrevious,
        onSwipeToNext = showNext,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
    )
}

@Composable
private fun PageNumber(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = Color.White,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun CloseButton(
    uiState: ImageViewerScreenUiState,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = uiState.listener::onClickClose,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White,
        ),
        modifier = modifier,
    ) {
        Icon(
            imageVector = AppIcons.Close,
            contentDescription = "閉じる",
        )
    }
}
