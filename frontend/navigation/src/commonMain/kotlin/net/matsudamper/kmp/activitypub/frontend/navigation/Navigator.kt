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

    /**
     * 積んである画面を捨てて [screen] だけにする。ログイン後に戻るでログイン画面へ戻れないようにする
     */
    fun replaceAll(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }
}
