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
     * URL で直接開いた画面の下に、アプリの中で戻ったときに出す画面も積む
     */
    fun initialScreens(unknownPathScreen: Screen): List<Screen> {
        val screen = screenOf(path = window.location.pathname, unknownPathScreen = unknownPathScreen)
        return generateSequence(screen) { it.parentScreen }.toList().asReversed()
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

    private fun screenOf(
        path: String,
        unknownPathScreen: Screen,
    ): Screen = Screen.fromPath(path) ?: unknownPathScreen

    /**
     * 履歴の各エントリの state に、履歴上の位置を持たせて積み上げとの対応を取る
     */
    private class BackStackHistorySync(
        private val backStack: NavBackStack<NavKey>,
        private val unknownPathScreen: Screen,
    ) {
        private var stackBottomHistoryIndex = 0
        private var currentHistoryIndex = 0
        private val popStateListener: (Event) -> Unit = { event ->
            onPopState(historyIndexOf((event as PopStateEvent).state))
        }

        fun start() {
            val reloadedHistoryIndex = historyIndexOf(window.history.state)
            if (reloadedHistoryIndex != null && reloadedHistoryIndex >= backStack.lastIndex) {
                // 再読み込み。下に積んだ画面の履歴は前回の起動で載せてあるので、そのまま使う
                stackBottomHistoryIndex = reloadedHistoryIndex - backStack.lastIndex
                window.history.replaceState(reloadedHistoryIndex.toJsNumber(), "", pathOf(backStack.last()))
            } else {
                stackBottomHistoryIndex = reloadedHistoryIndex ?: 0
                backStack.forEachIndexed { stackIndex, key ->
                    val historyIndex = stackBottomHistoryIndex + stackIndex
                    if (stackIndex == 0) {
                        window.history.replaceState(historyIndex.toJsNumber(), "", pathOf(key))
                    } else {
                        window.history.pushState(historyIndex.toJsNumber(), "", pathOf(key))
                    }
                }
            }
            currentHistoryIndex = stackBottomHistoryIndex + backStack.lastIndex
            window.addEventListener("popstate", popStateListener)
        }

        fun stop() {
            window.removeEventListener("popstate", popStateListener)
        }

        fun onBackStackChanged(stack: List<NavKey>) {
            if (stack.isEmpty()) return
            val targetHistoryIndex = stackBottomHistoryIndex + stack.lastIndex
            when {
                targetHistoryIndex > currentHistoryIndex -> {
                    for (historyIndex in currentHistoryIndex + 1..targetHistoryIndex) {
                        val key = stack[historyIndex - stackBottomHistoryIndex]
                        window.history.pushState(historyIndex.toJsNumber(), "", pathOf(key))
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
                        window.history.replaceState(currentHistoryIndex.toJsNumber(), "", path)
                    }
                }
            }
        }

        private fun onPopState(historyIndex: Int?) {
            val screen = screenOf(path = window.location.pathname, unknownPathScreen = unknownPathScreen)
            if (historyIndex == null) {
                // このアプリが積んでいない履歴。位置が分からないので、そこを起点に積み直す
                stackBottomHistoryIndex = 0
                currentHistoryIndex = 0
                window.history.replaceState(0.toJsNumber(), "", window.location.pathname)
                resetBackStack(screen)
                return
            }
            val stackIndex = historyIndex - stackBottomHistoryIndex
            when {
                // 再読み込みより前の履歴に戻った
                stackIndex < 0 -> {
                    stackBottomHistoryIndex = historyIndex
                    resetBackStack(screen)
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
        }

        private fun pathOf(key: NavKey): String = ((key as? Screen) ?: unknownPathScreen).path

        private fun resetBackStack(screen: Screen) {
            backStack.clear()
            backStack.add(screen)
        }

        private fun historyIndexOf(state: JsAny?): Int? {
            return (state as? JsNumber)?.toInt()
        }
    }
}
