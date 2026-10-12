package net.matsudamper.kmp.activitypub.frontend.navigation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf

@Stable
class BackStackNavigator(startScreen: Screen) : Navigator {
    private val backStack = mutableStateListOf(startScreen)

    val currentScreen: Screen
        get() = backStack.last()

    val backEnabled: Boolean
        get() = backStack.size > 1

    override fun navigate(screen: Screen) {
        backStack.add(screen)
    }

    override fun replaceAll(screen: Screen) {
        backStack.clear()
        backStack.add(screen)
    }

    override fun back() {
        if (backEnabled) {
            backStack.removeAt(backStack.lastIndex)
        }
    }
}
