package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.compose.runtime.Stable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

@Stable
class BackStackNavigator(
    private val backStack: NavBackStack<NavKey>,
) : Navigator {
    override fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    override fun back() {
        if (backStack.size > 1) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    override fun replaceAll(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }
}
