package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.material3.TabPosition
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
): TabIndicatorPosition {
    val currentTab = tabPositions.getOrNull(currentPage) ?: return TabIndicatorPosition(left = 0.dp, width = 0.dp)
    val targetPage = if (currentPageOffsetFraction > 0f) currentPage + 1 else currentPage - 1
    val targetTab = tabPositions.getOrNull(targetPage) ?: currentTab
    val fraction = abs(currentPageOffsetFraction)
    val width = lerp(currentTab.contentWidth, targetTab.contentWidth, fraction)
    val center = lerp(
        currentTab.left + currentTab.width / 2,
        targetTab.left + targetTab.width / 2,
        fraction,
    )
    return TabIndicatorPosition(left = center - width / 2, width = width)
}
