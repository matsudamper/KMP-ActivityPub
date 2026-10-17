package net.matsudamper.kmp.activitypub.frontend.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import net.matsudamper.kmp.activitypub.frontend.navigation.Navigator
import net.matsudamper.kmp.activitypub.frontend.navigation.Screen
import net.matsudamper.kmp.activitypub.frontend.screen.home.HomeScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.LoginScreen
import net.matsudamper.kmp.activitypub.frontend.screen.login.ServerSettingUiState
import net.matsudamper.kmp.activitypub.frontend.screen.logingate.LoginGate
import net.matsudamper.kmp.activitypub.frontend.screen.post.PostScreen

@Composable
internal fun ScreenNavigationHost(
    backStack: NavBackStack<NavKey>,
    serverSettingUiState: @Composable () -> ServerSettingUiState?,
    modifier: Modifier = Modifier,
) {
    val navigator = remember(backStack) { Navigator(backStack) }
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = { key ->
            NavEntry(key) {
                val screen = key as Screen
                if (screen.loginRequired) {
                    LoginGate(navigator = navigator) {
                        ScreenContent(
                            screen = screen,
                            navigator = navigator,
                            serverSettingUiState = serverSettingUiState,
                        )
                    }
                } else {
                    ScreenContent(
                        screen = screen,
                        navigator = navigator,
                        serverSettingUiState = serverSettingUiState,
                    )
                }
            }
        },
    )
}

@Composable
private fun ScreenContent(
    screen: Screen,
    navigator: Navigator,
    serverSettingUiState: @Composable () -> ServerSettingUiState?,
) {
    when (screen) {
        Screen.Login -> {
            LoginScreen(
                navigator = navigator,
                serverSettingUiState = serverSettingUiState(),
            )
        }

        Screen.Home -> {
            HomeScreen(navigator = navigator)
        }

        Screen.Post -> {
            PostScreen(navigator = navigator)
        }
    }
}
