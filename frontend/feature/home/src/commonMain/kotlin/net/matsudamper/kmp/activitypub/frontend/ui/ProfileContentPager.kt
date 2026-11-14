package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * タブを押し直したときに先頭へ戻すため、画面で持てるようにする
 */
@Stable
internal class ProfileContentPagerState(
    val headerListState: LazyListState,
    private val postsGridState: LazyGridState,
    private val mediaGridState: LazyGridState,
    private val likesGridState: LazyGridState,
) {
    fun gridState(tab: ProfileContentTab): LazyGridState {
        return when (tab) {
            ProfileContentTab.Posts -> postsGridState
            ProfileContentTab.Media -> mediaGridState
            ProfileContentTab.Likes -> likesGridState
        }
    }

    /**
     * 一度も出していないタブのグリッドは、最初のレイアウトまで scrollToItem が戻らないことがあるので、見出しを先に戻す
     */
    suspend fun scrollToTop() {
        headerListState.animateScrollToItem(0)
        postsGridState.scrollToItem(0)
        mediaGridState.scrollToItem(0)
        likesGridState.scrollToItem(0)
    }
}

@Composable
internal fun rememberProfileContentPagerState(): ProfileContentPagerState {
    val headerListState = rememberLazyListState()
    val postsGridState = rememberLazyGridState()
    val mediaGridState = rememberLazyGridState()
    val likesGridState = rememberLazyGridState()
    return remember(headerListState, postsGridState, mediaGridState, likesGridState) {
        ProfileContentPagerState(
            headerListState = headerListState,
            postsGridState = postsGridState,
            mediaGridState = mediaGridState,
            likesGridState = likesGridState,
        )
    }
}

/**
 * プロフィールの見出しの下に投稿やメディアのタブを並べ、左右にスワイプして切り替える。
 * 上へスクロールすると先に見出しが隠れ、タブが上に残る
 *
 * @param tabs 並べるタブ。[selectedTab] が入っていなければ先頭のタブを出す
 */
@Composable
internal fun ProfileContentPager(
    state: ProfileContentPagerState,
    tabs: List<ProfileContentTab>,
    selectedTab: ProfileContentTab,
    onSelectTab: (ProfileContentTab) -> Unit,
    header: LazyListScope.() -> Unit,
    page: LazyGridScope.(ProfileContentTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedPage = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val latestTabs by rememberUpdatedState(tabs)
    val pagerState = rememberPagerState(initialPage = selectedPage) { latestTabs.size }
    val latestSelectedTab by rememberUpdatedState(selectedTab)
    val latestOnSelectTab by rememberUpdatedState(onSelectTab)
    LaunchedEffect(pagerState, selectedPage) {
        // 途中で止まったアニメーションのずれが残るので、ページの番号だけでなくずれも見る
        val pageSettledOnSelected = pagerState.currentPage == selectedPage && pagerState.currentPageOffsetFraction == 0f
        if (!pageSettledOnSelected) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { settledPage ->
                val settledTab = latestTabs.getOrNull(settledPage) ?: return@collect
                if (settledTab != latestSelectedTab) {
                    latestOnSelectTab(settledTab)
                }
            }
    }
    val headerCollapsingConnection = remember(state.headerListState) {
        HeaderCollapsingConnection(headerListState = state.headerListState)
    }
    LazyColumn(
        state = state.headerListState,
        modifier = modifier.nestedScroll(headerCollapsingConnection),
    ) {
        header()
        item {
            Column(modifier = Modifier.fillParentMaxSize()) {
                ProfileContentTabRow(
                    tabs = tabs,
                    selectedTab = tabs.getOrElse(pagerState.currentPage) { tabs.first() },
                    onClickTab = onSelectTab,
                )
                val currentGridDragged by state.gridState(tabs.getOrElse(pagerState.currentPage) { tabs.first() })
                    .interactionSource
                    .collectIsDraggedAsState()
                HorizontalPager(
                    state = pagerState,
                    // 縦に動かしたあと指を止めたまま横へ動かすと、縦のドラッグが横の動きを取らずにページへ渡ってしまう
                    userScrollEnabled = !currentGridDragged,
                    modifier = Modifier.weight(1f),
                ) { pageIndex ->
                    val tab = tabs.getOrElse(pageIndex) { tabs.first() }
                    LazyVerticalGrid(
                        columns = ProfileGridCells,
                        state = state.gridState(tab),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        page(tab)
                    }
                }
            }
        }
    }
}

/**
 * 中のリストより先に見出しを隠す。下へ戻すときは中のリストが先頭に着いてから見出しが出る
 */
private class HeaderCollapsingConnection(
    private val headerListState: LazyListState,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f) return Offset.Zero
        val consumedByHeader = headerListState.dispatchRawDelta(-available.y)
        return Offset(x = 0f, y = -consumedByHeader)
    }
}
