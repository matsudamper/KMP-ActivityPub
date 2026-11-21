package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.TabIndicatorScope
import androidx.compose.material3.TabPosition
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import kotlin.math.abs

internal data class TabIndicatorPosition(
    val left: Dp,
    val width: Dp,
)

/**
 * スワイプ中のページのずれに合わせて、今のタブと向かう先のタブの間に下線を置く
 */
internal fun pagerIndicatorPosition(
    tabPositions: List<TabPosition>,
    currentPage: Int,
    currentPageOffsetFraction: Float,
    matchContentWidth: Boolean,
): TabIndicatorPosition {
    val currentTab = tabPositions.getOrNull(currentPage) ?: return TabIndicatorPosition(left = 0.dp, width = 0.dp)
    val targetPage = if (currentPageOffsetFraction > 0f) currentPage + 1 else currentPage - 1
    val targetTab = tabPositions.getOrNull(targetPage) ?: currentTab
    val fraction = abs(currentPageOffsetFraction)
    val width = if (matchContentWidth) {
        lerp(currentTab.contentWidth, targetTab.contentWidth, fraction)
    } else {
        lerp(currentTab.width, targetTab.width, fraction)
    }
    val center = lerp(
        currentTab.left + currentTab.width / 2,
        targetTab.left + targetTab.width / 2,
        fraction,
    )
    return TabIndicatorPosition(left = center - width / 2, width = width)
}

/**
 * 固定幅のタブ行の下線を、スワイプの途中にもページのずれに合わせて動かす
 */
internal fun TabIndicatorScope.fixedTabRowPagerIndicatorModifier(
    pagerState: PagerState,
    matchContentWidth: Boolean,
): Modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
    val indicatorPosition = pagerIndicatorPosition(
        tabPositions = tabPositions,
        currentPage = pagerState.currentPage,
        currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
        matchContentWidth = matchContentWidth,
    )
    val indicatorWidth = indicatorPosition.width.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = indicatorWidth, maxWidth = indicatorWidth),
    )
    // タブ行は下線の枠をタブ 1 つ分の幅に固定して開始端に置くので、その幅のまま返さないと中央に寄せられてずれる
    layout(constraints.maxWidth, placeable.height) {
        placeable.placeRelative(x = indicatorPosition.left.roundToPx(), y = 0)
    }
}
