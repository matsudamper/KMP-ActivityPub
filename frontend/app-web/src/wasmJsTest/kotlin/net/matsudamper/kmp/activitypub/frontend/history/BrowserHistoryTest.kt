package net.matsudamper.kmp.activitypub.frontend.history

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.browser.window
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import org.w3c.dom.events.Event

class BrowserHistoryTest {
    @Test
    fun directChildPathsKeepHomeParentAndBrowserHistory() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                listOf(Screen.Settings, Screen.Post, Screen.ProfileEdit).forEach { screen ->
                    window.history.replaceState(null, "", screen.path)
                    val initialScreens = BrowserHistory.initialScreens(Screen.Login)
                    assertEquals(listOf(Screen.Home.Timeline, screen), initialScreens)
                    val backStack = NavBackStack<NavKey>(*initialScreens.toTypedArray())
                    val sync = BrowserHistory.BackStackHistorySync(backStack, Screen.Login)
                    sync.start()
                    try {
                        awaitPopState { window.history.back() }
                        assertHome(backStack, Screen.Home.Tab.Timeline)
                        sync.onBackStackChanged(backStack.toList())
                        awaitPopState { window.history.forward() }
                        assertEquals(initialScreens, backStack.toList())
                        assertEquals(screen.path, window.location.pathname)
                    } finally {
                        sync.stop()
                    }
                }
            }
        }
    }

    @Test
    fun homeBackUpdatesUrlWithoutRemovingHome() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                window.history.replaceState(null, "", "/search")
                val backStack = NavBackStack<NavKey>(*BrowserHistory.initialScreens(Screen.Login).toTypedArray())
                val navigator = Navigator(backStack)
                val sync = BrowserHistory.BackStackHistorySync(backStack, Screen.Login)
                sync.start()
                try {
                    navigator.back()
                    sync.onBackStackChanged(backStack.toList())
                    assertHome(backStack, Screen.Home.Tab.Timeline)
                    awaitPopState { window.history.back() }
                    assertHome(backStack, Screen.Home.Tab.Search)
                    sync.onBackStackChanged(backStack.toList())
                    awaitPopState { window.history.forward() }
                    assertHome(backStack, Screen.Home.Tab.Timeline)
                } finally {
                    sync.stop()
                }
            }
        }
    }

    @Test
    fun directHomePathsCreateOneEntry() {
        Screen.Home.Tab.entries.forEach { tab ->
            val screen = Screen.Home(tab)
            window.history.replaceState(null, "", screen.path)
            assertEquals(listOf(screen), BrowserHistory.initialScreens(Screen.Login))
        }
    }

    @Test
    fun tabsFollowBrowserHistoryAndSurviveReload() = runTest {
        withContext(Dispatchers.Default) {
            withTimeout(10_000) {
                window.history.replaceState(null, "", "/home")
                val backStack = NavBackStack<NavKey>(*BrowserHistory.initialScreens(Screen.Login).toTypedArray())
                val navigator = Navigator(backStack)
                val sync = BrowserHistory.BackStackHistorySync(backStack, Screen.Login)
                sync.start()
                try {
                    navigator.selectHomeTab(Screen.Home.Tab.Search)
                    sync.onBackStackChanged(backStack.toList())
                    assertHome(backStack, Screen.Home.Tab.Search)
                    navigator.selectHomeTab(Screen.Home.Tab.User)
                    sync.onBackStackChanged(backStack.toList())
                    assertHome(backStack, Screen.Home.Tab.User)

                    awaitPopState { window.history.back() }
                    assertHome(backStack, Screen.Home.Tab.Search)
                    sync.onBackStackChanged(backStack.toList())
                    awaitPopState { window.history.forward() }
                    assertHome(backStack, Screen.Home.Tab.User)
                    awaitPopState { window.history.go(-2) }
                    assertHome(backStack, Screen.Home.Tab.Timeline)

                    navigator.selectHomeTab(Screen.Home.Tab.Search)
                    sync.onBackStackChanged(backStack.toList())
                    navigator.navigate(Screen.Settings)
                    sync.onBackStackChanged(backStack.toList())
                    assertEquals("/settings", window.location.pathname)
                    awaitPopState {
                        navigator.back()
                        sync.onBackStackChanged(backStack.toList())
                    }
                    assertHome(backStack, Screen.Home.Tab.Search)
                } finally {
                    sync.stop()
                }

                val reloadedStack = NavBackStack<NavKey>(*BrowserHistory.initialScreens(Screen.Login).toTypedArray())
                val reloadedSync = BrowserHistory.BackStackHistorySync(reloadedStack, Screen.Login)
                reloadedSync.start()
                try {
                    assertHome(reloadedStack, Screen.Home.Tab.Search)
                    awaitPopState { window.history.back() }
                    assertHome(reloadedStack, Screen.Home.Tab.Timeline)
                    reloadedSync.onBackStackChanged(reloadedStack.toList())
                    awaitPopState { window.history.forward() }
                    assertHome(reloadedStack, Screen.Home.Tab.Search)
                    awaitPopState { window.history.forward() }
                    assertEquals(listOf(Screen.Home(Screen.Home.Tab.Search), Screen.Settings), reloadedStack.toList())
                    assertEquals("/settings", window.location.pathname)
                } finally {
                    reloadedSync.stop()
                }
            }
        }
    }

    private fun assertHome(backStack: NavBackStack<NavKey>, tab: Screen.Home.Tab) {
        val home = Screen.Home(tab)
        assertEquals(listOf(home), backStack.toList())
        assertEquals(home.path, window.location.pathname)
    }

    private suspend fun awaitPopState(action: () -> Unit) {
        val popped = CompletableDeferred<Unit>()
        val listener: (Event) -> Unit = { popped.complete(Unit) }
        window.addEventListener("popstate", listener)
        try {
            action()
            popped.await()
        } finally {
            window.removeEventListener("popstate", listener)
        }
    }
}
