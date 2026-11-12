package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/**
 * 引っ張って読み直す枠。アプリの引っ張って読み直す操作は、すべてこれを使う。
 *
 * 読み直しがすぐ終わるとインジケーターが一瞬で消えて、読み直したのか分からないので、
 * 引っ張ってから [PullToRefreshMinDuration] の間はインジケーターを出し続ける。
 * 呼び出し側の [refreshing] は読み直しの間だけ true にすればよい。
 *
 * マウスホイールで上端を越えて回したときは読み直さない。
 *
 * @param scrollableModifier 枠の中のスクロールする部品に付ける。ホイールで読み直さないための判定に使う
 */
@Composable
fun AppPullToRefreshBox(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(scrollableModifier: Modifier) -> Unit,
) {
    var pullCount by remember { mutableIntStateOf(0) }
    var holdingIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(pullCount) {
        if (pullCount == 0) return@LaunchedEffect
        holdingIndicator = true
        delay(PullToRefreshMinDuration)
        holdingIndicator = false
    }
    val wheelPullToRefreshBlocker = remember { WheelPullToRefreshBlocker() }
    PullToRefreshBox(
        isRefreshing = refreshing || holdingIndicator,
        onRefresh = {
            pullCount++
            onRefresh()
        },
        modifier = modifier,
    ) {
        content(wheelPullToRefreshBlocker.modifier)
    }
}

private val PullToRefreshMinDuration = 600.milliseconds

/**
 * ドラッグの移動量は指を離した後にまとめて処理されることがあるため、離した時点では判定を切り替えない。
 * 次にホイールが回るか押下されるまで、直前の入力の種類を保つ
 */
private class WheelPullToRefreshBlocker : NestedScrollConnection {
    val modifier: Modifier = Modifier
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    when (event.type) {
                        PointerEventType.Scroll -> lastScrollInputIsWheel = true
                        PointerEventType.Press -> lastScrollInputIsWheel = false
                        else -> Unit
                    }
                }
            }
        }
        .nestedScroll(this)

    private var lastScrollInputIsWheel = false

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        val pullOverscrollByWheel = source == NestedScrollSource.UserInput && lastScrollInputIsWheel && available.y > 0f
        return if (pullOverscrollByWheel) Offset(x = 0f, y = available.y) else Offset.Zero
    }
}
