package net.matsudamper.kmp.activitypub.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import net.matsudamper.kmp.activitypub.frontend.history.BrowserHistory
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenBackStack
import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureScreens
import net.matsudamper.kmp.activitypub.frontend.screen.HomeFeatureWebScreens
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
    val backStack = rememberScreenBackStack(startScreen = BrowserHistory.currentScreen())
    val navigator = remember(backStack) { Navigator(backStack) }
    BrowserHistory.SyncEffect(backStack)
    MaterialTheme {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<Screen.Login> {
                    HomeFeatureScreens.Login(
                        navigator = navigator,
                        serverSettingUiState = null,
                    )
                }
                entry<Screen.Home> {
                    HomeFeatureWebScreens.LoginRequired {
                        HomeFeatureScreens.Home(navigator = navigator)
                    }
                }
                entry<Screen.Post> {
                    HomeFeatureWebScreens.LoginRequired {
                        HomeFeatureScreens.Post(navigator = navigator)
                    }
                }
            },
        )
    }
}
