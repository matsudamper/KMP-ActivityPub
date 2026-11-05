package net.matsudamper.kmp.activitypub.frontend

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import net.matsudamper.kmp.activitypub.frontend.history.BrowserHistory
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeMode
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeSettingStore
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenBackStack
import net.matsudamper.kmp.activitypub.frontend.screen.HomeNavigationHost
import net.matsudamper.kmp.activitypub.frontend.screen.theme.AppTheme
import net.matsudamper.kmp.activitypub.frontend.screen.theme.isDarkTheme
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.plugin.module.dsl.koinConfiguration

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    normalizeNavigatorLanguageForCjkFontFallback()
    ComposeViewport {
        App()
    }
}

@Composable
private fun App() {
    KoinApplication(configuration = koinConfiguration<WebKoinApp>()) {
        AppNavigation()
    }
}

@Composable
private fun AppNavigation() {
    val backStack = rememberScreenBackStack(startScreens = BrowserHistory.initialScreens(unknownPathScreen = UNKNOWN_PATH_SCREEN))
    BrowserHistory.SyncEffect(backStack = backStack, unknownPathScreen = UNKNOWN_PATH_SCREEN)
    val themeSettingStore = koinInject<ThemeSettingStore>()
    val themeMode by themeSettingStore.themeModeFlow.collectAsState(initial = ThemeMode.System)
    AppTheme(darkTheme = themeMode.isDarkTheme()) {
        HomeNavigationHost(
            backStack = backStack,
            serverSettingUiState = { null },
        )
    }
}

private val UNKNOWN_PATH_SCREEN: Screen = Screen.Login
