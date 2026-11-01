package net.matsudamper.kmp.activitypub.android.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen

class HomeNavigationTest {
    @Test
    fun backFromRemoteProfileReturnsToSearch() {
        val search = Screen.Home(Screen.Home.Tab.Search)
        val backStack = NavBackStack<NavKey>(search)
        val navigator = Navigator(backStack)
        val profile = Screen.UserProfile("bob@remote.example")

        navigator.navigate(profile)
        assertEquals(profile, Screen.fromPath(profile.path))
        assertEquals(profile, backStack.last())
        navigator.back()
        assertEquals(listOf(search), backStack.toList())
    }

    @Test
    fun backFromOtherHomeTabsReturnsToTimeline() {
        listOf(Screen.Home.Tab.Search, Screen.Home.Tab.User).forEach { tab ->
            val backStack = NavBackStack<NavKey>(Screen.Home(tab))
            val navigator = Navigator(backStack)

            assertTrue(navigator.homeTabBackEnabled)
            navigator.back()
            assertEquals(listOf(Screen.Home.Timeline), backStack.toList())
            assertFalse(navigator.homeTabBackEnabled)
            navigator.back()
            assertEquals(listOf(Screen.Home.Timeline), backStack.toList())
        }
    }

    @Test
    fun tabChangesKeepOneHomeEntry() {
        val backStack = NavBackStack<NavKey>(Screen.Home.Timeline)
        val navigator = Navigator(backStack)

        listOf(Screen.Home.Tab.Search, Screen.Home.Tab.User, Screen.Home.Tab.Timeline).forEach { tab ->
            navigator.selectHomeTab(tab)
            assertEquals(listOf(Screen.Home(tab)), backStack.toList())
        }
    }

    @Test
    fun returningFromSettingsPreservesSelectedTab() {
        val backStack = NavBackStack<NavKey>(Screen.Home.Timeline)
        val navigator = Navigator(backStack)
        navigator.selectHomeTab(Screen.Home.Tab.Search)
        navigator.navigate(Screen.Settings)
        assertFalse(navigator.homeTabBackEnabled)
        navigator.back()

        assertEquals(listOf(Screen.Home(Screen.Home.Tab.Search)), backStack.toList())
        assertTrue(navigator.homeTabBackEnabled)
        navigator.back()
        assertEquals(listOf(Screen.Home.Timeline), backStack.toList())
    }

    @Test
    fun directHomePathsHaveNoAdditionalHomeParent() {
        Screen.Home.Tab.entries.forEach { tab ->
            val home = Screen.Home(tab)
            assertEquals(home, Screen.fromPath(home.path))
            assertNull(home.parentScreen)
        }
    }
}
