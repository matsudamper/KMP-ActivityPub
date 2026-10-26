package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

@Stable
class Navigator(
    private val backStack: NavBackStack<NavKey>,
) {
    fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    fun back() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    /**
     * [screen] が積んであればそこまで戻り、無ければ積む。
     * 同じ画面を 2 つ積むと、画面ごとの状態の保存先がぶつかる
     */
    fun navigateOrBackTo(screen: Screen) {
        val index = backStack.indexOf(screen).takeIf { it >= 0 }
        if (index == null) {
            backStack.add(screen)
        } else {
            while (backStack.lastIndex > index) {
                backStack.removeAt(backStack.lastIndex)
            }
        }
    }

    /**
     * 積んである画面を捨てて [screen] だけにする。ログイン後に戻るでログイン画面へ戻れないようにする
     */
    fun replaceAll(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }
}
