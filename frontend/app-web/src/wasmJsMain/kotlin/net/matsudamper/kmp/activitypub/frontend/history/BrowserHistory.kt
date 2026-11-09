package net.matsudamper.kmp.activitypub.frontend.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.browser.window
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import org.w3c.dom.PopStateEvent
import org.w3c.dom.events.Event

internal object BrowserHistory {
    /**
     * 再読み込みなら履歴に残した積み上げを戻す。
     * URL で直接開いたときは、その画面の下にアプリの中で戻ったときに出す画面も積む
     */
    fun initialScreens(unknownPathScreen: Screen): List<Screen> {
        val savedScreens = historyEntryOf(window.history.state)?.paths
            ?.takeIf { it.lastOrNull() == window.location.pathname }
            ?.map { screenOf(path = it, unknownPathScreen = unknownPathScreen) }
        if (savedScreens != null) return savedScreens
        val screen = screenOf(path = window.location.pathname, unknownPathScreen = unknownPathScreen)
        return screensWithParents(screen)
    }

    /**
     * 画面の積み上げをブラウザの履歴と URL に写し、ブラウザの戻る/進むを積み上げに反映する
     */
    @Composable
    fun SyncEffect(
        backStack: NavBackStack<NavKey>,
        unknownPathScreen: Screen,
    ) {
        val sync = remember(backStack, unknownPathScreen) {
            BackStackHistorySync(backStack = backStack, unknownPathScreen = unknownPathScreen)
        }
        DisposableEffect(sync) {
            sync.start()
            onDispose { sync.stop() }
        }
        LaunchedEffect(sync) {
            snapshotFlow { backStack.toList() }.collect { sync.onBackStackChanged(it) }
        }
    }

    /**
     * 履歴の各エントリの state に、履歴上の位置とその時点の積み上げを持たせて積み上げとの対応を取る。
     * 積み上げを持たせないと、再読み込みのあとに親画面の無い画面からアプリの中で戻れなくなる
     */
    internal class BackStackHistorySync(
        private val backStack: NavBackStack<NavKey>,
        private val unknownPathScreen: Screen,
    ) {
        private var syncedStack: List<NavKey> = listOf()
        private var stackBottomHistoryIndex = 0
        private var currentHistoryIndex = 0
        private val popStateListener: (Event) -> Unit = { event ->
            onPopState(historyEntryOf((event as PopStateEvent).state))
        }

        fun start() {
            val reloadedHistoryIndex = historyEntryOf(window.history.state)?.index
            if (reloadedHistoryIndex != null && reloadedHistoryIndex >= backStack.lastIndex) {
                // 再読み込み。下に積んだ画面の履歴は前回の起動で載せてあるので、そのまま使う
                stackBottomHistoryIndex = reloadedHistoryIndex - backStack.lastIndex
                window.history.replaceState(historyStateOf(reloadedHistoryIndex, backStack), "", pathOf(backStack.last()))
            } else {
                stackBottomHistoryIndex = reloadedHistoryIndex ?: 0
                backStack.indices.forEach { stackIndex ->
                    val historyIndex = stackBottomHistoryIndex + stackIndex
                    val stack = backStack.take(stackIndex + 1)
                    if (stackIndex == 0) {
                        window.history.replaceState(historyStateOf(historyIndex, stack), "", pathOf(stack.last()))
                    } else {
                        window.history.pushState(historyStateOf(historyIndex, stack), "", pathOf(stack.last()))
                    }
                }
            }
            currentHistoryIndex = stackBottomHistoryIndex + backStack.lastIndex
            syncedStack = backStack.toList()
            window.addEventListener("popstate", popStateListener)
        }

        fun stop() {
            window.removeEventListener("popstate", popStateListener)
        }

        fun onBackStackChanged(stack: List<NavKey>) {
            if (stack.isEmpty()) return
            val home = stack.last() as? Screen.Home
            val previousHome = syncedStack.lastOrNull() as? Screen.Home
            if (stack.size == syncedStack.size && home != null && previousHome != null && home != previousHome) {
                stackBottomHistoryIndex += 1
            }
            val targetHistoryIndex = stackBottomHistoryIndex + stack.lastIndex
            when {
                targetHistoryIndex > currentHistoryIndex -> {
                    for (historyIndex in currentHistoryIndex + 1..targetHistoryIndex) {
                        val pushedStack = stack.take(historyIndex - stackBottomHistoryIndex + 1)
                        window.history.pushState(historyStateOf(historyIndex, pushedStack), "", pathOf(pushedStack.last()))
                    }
                    currentHistoryIndex = targetHistoryIndex
                }

                targetHistoryIndex < currentHistoryIndex -> {
                    // go は非同期で popstate が後から来る。その時点で積み上げは既に一致しているので何も起きない
                    window.history.go(targetHistoryIndex - currentHistoryIndex)
                    currentHistoryIndex = targetHistoryIndex
                }

                else -> {
                    val path = pathOf(stack.last())
                    if (window.location.pathname != path) {
                        window.history.replaceState(historyStateOf(currentHistoryIndex, stack), "", path)
                    }
                }
            }
            syncedStack = stack
        }

        private fun onPopState(historyEntry: HistoryEntry?) {
            val screen = screenOf(path = window.location.pathname, unknownPathScreen = unknownPathScreen)
            if (historyEntry == null) {
                // このアプリが積んでいない履歴。位置が分からないので、そこを起点に積み直す
                stackBottomHistoryIndex = 0
                currentHistoryIndex = 0
                resetBackStack(screen)
                window.history.replaceState(historyStateOf(0, backStack), "", window.location.pathname)
                return
            }
            val historyIndex = historyEntry.index
            val stackIndex = historyIndex - stackBottomHistoryIndex
            val savedScreens = savedScreensOf(historyEntry)
            when {
                screen is Screen.Home -> {
                    stackBottomHistoryIndex = historyIndex
                    resetBackStack(screen)
                }

                // 再読み込みより前の履歴に戻ったときなど。その時点の積み上げを戻さないと、ブラウザでは戻れるのにアプリの中で戻れなくなる
                savedScreens != null && (stackIndex !in backStack.indices || backStack.take(stackIndex + 1) != savedScreens) -> {
                    restoreBackStack(screens = savedScreens, historyIndex = historyIndex)
                }

                // 積み上げを持たない古い履歴。親画面を積む。履歴の 0 番より下には積まない。そこへ戻る履歴が無く、アプリの中で戻ったときに URL と食い違う
                stackIndex < 0 -> {
                    restoreBackStack(screens = screensWithParents(screen).takeLast(historyIndex + 1), historyIndex = historyIndex)
                }

                stackIndex <= backStack.lastIndex -> {
                    while (backStack.lastIndex > stackIndex) {
                        backStack.removeAt(backStack.lastIndex)
                    }
                    if (backStack.last() != screen) {
                        backStack[stackIndex] = screen
                    }
                }

                // 進むで複数飛ばされると間の画面は分からないので、着いた画面だけ積む
                else -> {
                    backStack.add(screen)
                    stackBottomHistoryIndex = historyIndex - backStack.lastIndex
                }
            }
            currentHistoryIndex = historyIndex
            syncedStack = backStack.toList()
        }

        private fun savedScreensOf(historyEntry: HistoryEntry): List<Screen>? {
            return historyEntry.paths
                .takeIf { it.lastOrNull() == window.location.pathname }
                ?.map { screenOf(path = it, unknownPathScreen = unknownPathScreen) }
        }

        private fun restoreBackStack(screens: List<Screen>, historyIndex: Int) {
            stackBottomHistoryIndex = historyIndex - screens.lastIndex
            backStack.clear()
            backStack.addAll(screens)
            syncedStack = backStack.toList()
        }

        private fun pathOf(key: NavKey): String = ((key as? Screen) ?: unknownPathScreen).path

        private fun resetBackStack(screen: Screen) {
            backStack.clear()
            backStack.add(screen)
            syncedStack = backStack.toList()
        }

        private fun historyStateOf(historyIndex: Int, stack: List<NavKey>): JsString {
            return (listOf(historyIndex.toString()) + stack.map(::pathOf)).joinToString(HISTORY_STATE_SEPARATOR).toJsString()
        }
    }

    /**
     * @param paths このエントリを積んだ時点の積み上げ。下から順に並ぶ
     */
    private class HistoryEntry(
        val index: Int,
        val paths: List<String>,
    )

    /**
     * パスは改行を含まないので、位置と積み上げのパスを改行でつないで 1 つの文字列にする
     */
    private const val HISTORY_STATE_SEPARATOR = "\n"

    private fun historyEntryOf(state: JsAny?): HistoryEntry? {
        if (state is JsNumber) return HistoryEntry(index = state.toInt(), paths = listOf())
        val lines = (state as? JsString)?.toString()?.split(HISTORY_STATE_SEPARATOR) ?: return null
        val index = lines.first().toIntOrNull() ?: return null
        return HistoryEntry(index = index, paths = lines.drop(1))
    }

    private fun screensWithParents(screen: Screen): List<Screen> {
        return generateSequence(screen) { it.parentScreen }.toList().asReversed()
    }

    private fun screenOf(
        path: String,
        unknownPathScreen: Screen,
    ): Screen = Screen.fromPath(path) ?: unknownPathScreen
}
