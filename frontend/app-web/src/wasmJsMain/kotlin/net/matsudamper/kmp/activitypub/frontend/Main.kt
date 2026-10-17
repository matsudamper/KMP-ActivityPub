package net.matsudamper.kmp.activitypub.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import net.matsudamper.kmp.activitypub.frontend.history.BrowserHistory
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenBackStack
import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureScreens
import org.koin.compose.KoinApplication
import org.koin.plugin.module.dsl.koinConfiguration

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
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
    MaterialTheme {
        HomeFeatureScreens.NavigationHost(
            backStack = backStack,
            serverSettingUiState = { null },
        )
    }
}

private val UNKNOWN_PATH_SCREEN: Screen = Screen.Login
