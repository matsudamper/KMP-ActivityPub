package net.matsudamper.kmp.activitypub.frontend.ui

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
) {
    fun gridState(tab: ProfileContentTab): LazyGridState {
        return when (tab) {
            ProfileContentTab.Posts -> postsGridState
            ProfileContentTab.Media -> mediaGridState
        }
    }

    suspend fun scrollToTop() {
        postsGridState.scrollToItem(0)
        mediaGridState.scrollToItem(0)
        headerListState.animateScrollToItem(0)
    }
}

@Composable
internal fun rememberProfileContentPagerState(): ProfileContentPagerState {
    val headerListState = rememberLazyListState()
    val postsGridState = rememberLazyGridState()
    val mediaGridState = rememberLazyGridState()
    return remember(headerListState, postsGridState, mediaGridState) {
        ProfileContentPagerState(
            headerListState = headerListState,
            postsGridState = postsGridState,
            mediaGridState = mediaGridState,
        )
    }
}

/**
 * プロフィールの見出しの下に投稿とメディアのタブを並べ、左右にスワイプして切り替える。
 * 上へスクロールすると先に見出しが隠れ、タブが上に残る
 */
@Composable
internal fun ProfileContentPager(
    state: ProfileContentPagerState,
    selectedTab: ProfileContentTab,
    onSelectTab: (ProfileContentTab) -> Unit,
    header: LazyListScope.() -> Unit,
    page: LazyGridScope.(ProfileContentTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { ProfileContentTab.entries.size }
    val latestSelectedTab by rememberUpdatedState(selectedTab)
    val latestOnSelectTab by rememberUpdatedState(onSelectTab)
    LaunchedEffect(pagerState, selectedTab) {
        if (pagerState.settledPage != selectedTab.ordinal) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { settledPage ->
                val settledTab = ProfileContentTab.entries[settledPage]
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
                    selectedTab = ProfileContentTab.entries[pagerState.currentPage],
                    onClickTab = onSelectTab,
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                ) { pageIndex ->
                    val tab = ProfileContentTab.entries[pageIndex]
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
