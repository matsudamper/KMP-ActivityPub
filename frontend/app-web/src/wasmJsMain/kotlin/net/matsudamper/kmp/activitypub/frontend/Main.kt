package net.matsudamper.kmp.activitypub.frontend

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.browser.window
import net.matsudamper.kmp.activitypub.frontend.history.BrowserHistory
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.navigation.rememberScreenBackStack
import net.matsudamper.kmp.activitypub.frontend.screen.HomeNavigationHost
import net.matsudamper.kmp.activitypub.frontend.screen.ScreenPlatform
import net.matsudamper.kmp.activitypub.frontend.screen.admin.AdminAccountNewScreen
import net.matsudamper.kmp.activitypub.frontend.screen.admin.AdminAccountsScreen
import net.matsudamper.kmp.activitypub.frontend.screen.admin.AdminScreen
import net.matsudamper.kmp.activitypub.frontend.ui.RetainedScreenState
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
        HomeNavigationHost(
            backStack = backStack,
            serverSettingUiState = { null },
            otherScreenContent = { screen, navigator ->
                AdminScreenContent(screen = screen, navigator = navigator)
            },
        )
    }
}

@Composable
private fun AdminScreenContent(
    screen: Screen,
    navigator: Navigator,
) {
    val retainedScreenState = viewModel { RetainedScreenStateViewModel() }.retainedScreenState
    when (screen) {
        Screen.Admin -> AdminScreen(
            retainedScreenState = retainedScreenState,
            platform = WasmScreenPlatform,
            navController = navigator,
        )

        Screen.AdminAccounts -> AdminAccountsScreen(
            retainedScreenState = retainedScreenState,
            navController = navigator,
        )

        Screen.AdminAccountNew -> AdminAccountNewScreen(
            retainedScreenState = retainedScreenState,
            navController = navigator,
        )

        else -> Unit
    }
}

/**
 * 画面の ViewModel の置き場を履歴のエントリごとに持たせる。エントリが積まれている間は残る
 */
private class RetainedScreenStateViewModel : ViewModel() {
    val retainedScreenState: RetainedScreenState = RetainedScreenState(viewModelScope)

    override fun onCleared() {
        retainedScreenState.dispose()
    }
}

private object WasmScreenPlatform : ScreenPlatform {
    override fun openExternalLink(url: String) {
        window.open(url, "_blank", "noopener,noreferrer")
    }
}

private val UNKNOWN_PATH_SCREEN: Screen = Screen.Login
